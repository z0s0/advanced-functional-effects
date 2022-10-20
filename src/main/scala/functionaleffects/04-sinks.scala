/**
 * In ZIO Streams, Sinks are consumers of values. Not only do they consume
 * values, but they are free to produce remainders ("leftovers"), as well as
 * terminate with values of a given type (or error).
 *
 * Sinks are the duals of streams. While streams emit values, sinks absorb
 * them. To create a complete pipeline, a stream must be connected to a
 * sink, because values need a place to go. All the run methods of Stream
 * are actually implemented by connecting streams to sinks and running the
 * complete pipeline.
 */
package advancedfunctionaleffects.sinks

import zio._
import zio.stream._

import zio.test._
import zio.test.TestAspect._

// R is the "services" that the sink needs to run
// E is the error that the sink can fail with
// In is the input that the sink knows how to handle
// L is the type of the leftovers that the sink can produce
// Z is the summary value that the sink produces

// final class ZSink[-R, +E, -In, +L, +Z]

object SinkExample extends ZIOAppDefault {

  val stream = ZStream.fromIterable(1 to 10).rechunk(10)

  val sink = ZSink.collectAll[Int]
  // Got chunk that has ten elements
  // Sink takes first five elements and put them into a chunk

  val run =
    ???
}

object Constructors extends ZIOSpecDefault {
  def spec =
    suite("Constructors") {

      /**
       * EXERCISE
       *
       * Replace the call to `.runCount` by a call to `run` using `ZSink.count`.
       */
      test("count") {
        val stream = ZStream(1, 2, 3, 4)

        for {
          size <- stream.run(ZSink.count)
        } yield assertTrue(size.toInt == 4)
      } +
        /**
         * EXERCISE
         *
         * Replace the call to `.runCollect` by a call to `run` using
         * `ZSink.take(2)`.
         */
        test("take") {
          val stream = ZStream(1, 2, 3, 4)

          for {
            two <- stream.run(ZSink.take(2))
          } yield assertTrue(two == Chunk(1, 2))
        } +
        /**
         * EXERCISE
         *
         * Replace the call to `.runDrain` by a call to `run` using
         * `ZSink.foreach`, specifying a callback that increments the
         * ref by each value consumed by the sink.
         */
        test("foreach") {
          val stream = ZStream(1, 2, 3, 4)

          def incrementRefSink(ref: Ref[Int]): ZSink[Any, Nothing, Int, Nothing, Unit] =
            ZSink.foreach(v => ref.update(_ + v))

          for {
            ref <- Ref.make(0)
            _   <- stream.run(incrementRefSink(ref))
            v   <- ref.get
          } yield assertTrue(v == 10)
        } +
        /**
         * EXERCISE
         *
         * Replace the call to `.runSum` by a call to `run` using a
         * sink constructed with `ZSink.foldLeft` that multiplies all
         * consumed values together (producing their product).
         */
        test("foldLeft") {
          val stream = ZStream(1, 2, 3, 4)

          val productSink =
            ZSink.foldLeft[Int, Int](1)(_ * _)

          for {
            value <- stream.runFold(1)(_ * _)
          } yield assertTrue(value == 24)
        }
    }
}

object Operators extends ZIOSpecDefault {
  def spec =
    suite("Operators") {

      /**
       * EXERCISE
       *
       * Use the `.map` method of the provided `sink` to map the collected
       * values into the length of the collected values. Then replace the
       * call to `.runCount` with a call to `.run(sink)`.
       */
      test("map") {
        val sink = ZSink.collectAll[Int].map(_.length)

        val stream = ZStream(1, 2, 3, 4)

        for {
          value <- stream.run(sink)
        } yield assertTrue(value == 4)
      } +
        /**
         * EXERCISE
         *
         * Use `zipPar` to parallel zip `ZSink.count` and `ZSink.sum` together,
         * to produce a tuple of their outputs.
         */
        test("zipPar") {
          def zippedSink: ZSink[Any, Nothing, Int, Nothing, (Long, Int)] =
            ZSink.count.zipPar(ZSink.sum[Int])

          def loggingSink(span: String): ZSink[Any, Nothing, Any, Nothing, Any] =
            ZSink.logSpan(span)(ZSink.foreach(in => ZIO.logInfo(in.toString)))

          val averageSink: ZSink[Any, Nothing, Int, Nothing, Double] =
            ZSink.count.zipPar(ZSink.sum[Int]).map {
              case (count, sum) => sum.toDouble / count
            }

          val myFancySink: ZSink[Any, Nothing, Int, Nothing, Double] =
            averageSink zipParLeft loggingSink("fancySink")
            // ZSink.foldLeft[Int, (Long, Int)]((0L, 0)) {
            //   case ((count, sum), value) => (count + 1L, sum + value)
            // }
            // ZSink.sum
            // ZSink.count

          val stream = ZStream(1, 2, 3, 4)

          for {
            value <- stream.run(zippedSink)
          } yield assertTrue(value == (4L, 10))
        } +
        /**
         * EXERCISE
         *
         * Use `zip` to zip `ZSink.take(3)` and `ZSink.collectAll[Int]` together,
         * to produce a tuple of their outputs.
         */
        test("zip") {
          def zippedSink: ZSink[Any, Nothing, Int, Int, (Chunk[Int], Chunk[Int])] =
            ZSink.take[Int](3).zip(ZSink.collectAll[Int])

          val stream = ZStream(1, 2, 3, 4)

          for {
            value <- stream.run(zippedSink)
          } yield assertTrue(value == (Chunk(1, 2, 3), Chunk(4)))
        } +
        /**
         * EXERCISE
         *
         * Use `ZSink.take` and `ZSink.collectAll` in the provided `for`
         * comprehension to make the test pass.
         */
        test("flatMap") {
          val sink =
            for {
              size       <- ZSink.head[Int].map(_.get)
              three      <- ZSink.take[Int](size)
              threeAgain <- ZSink.take[Int](size)
            } yield (three, threeAgain)

          val stream = ZStream(3, 4, 5, 6, 7, 8, 9)

          for {
            chunks <- stream.run(sink)
          } yield assertTrue(chunks == (Chunk(4, 5, 6), Chunk(7, 8, 9)))
        }
    }
}

/**
 * GRADUATION
 *
 * To graduate from this section, you will implement a sink that writes results
 * to a persistet queue.
 */
object Graduation extends ZIOSpecDefault {
  trait PersistentQueue[-A] {
    def append(a: A): Task[Unit]

    def shutdown: Task[Unit]
  }

  trait PersistentQueueFactory {
    def create[A](topic: String): Task[PersistentQueue[A]]
  }

  object PersistentQueueFactory {
    val live: ZLayer[Any, Nothing, PersistentQueueFactory] =
      ZLayer.succeed { // ZLayer.fromZIO or ZLayer.scoped
        new PersistentQueueFactory {
          def create[A](topic: String): Task[PersistentQueue[A]] =
            ???
        }
      }
  }

  def persistentQueue[A](topic: String): ZSink[PersistentQueueFactory, Throwable, A, Nothing, Unit] =
    ZSink.serviceWithSink[PersistentQueueFactory] { factory =>
      ???
    }

  def spec =
    suite("Graduation") {
      test("persistentQueue") {
        val stream = ZStream(1, 2, 3, 4, 5)
        for {
          _ <- stream.run(persistentQueue("test"))
        } yield assertTrue(true)
      } @@ ignore
    }.provide(PersistentQueueFactory.live)
}
