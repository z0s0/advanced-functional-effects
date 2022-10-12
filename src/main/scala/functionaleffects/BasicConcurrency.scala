package functionaleffects

import zio._

object BasicConcurrency extends ZIOAppDefault {

  // Ref - functional effect equivalent of a AtomicReference

  // Promise - "box" that starts out empty and can only be filled once

  // Promise.make

  // Box that is either empty or filled with an E or filled with an A

  // trait Promise[E, A] {
  //   def await: ZIO[Any, E, A]
  //   def succeed(a: A): ZIO[Any, Nothing, Boolean]
  //   def fail(e: E): ZIO[Any, Nothing, Boolean]
  // }

  // import java.util.concurrent.atomic.AtomicReference

  // val ref = new AtomicReference[Int](0)
  // val x = ref.accumulateAndGet(10, _ + _)

  // trait Ref[A] {
  //   def get: ZIO[Any, Nothing, A]
  //   def set(a: A): ZIO[Any, Nothing, Unit]
  //   def update(f: A => A): ZIO[Any, Nothing, Unit]
  //   def modify[B](f: A => (B, A)): ZIO[Any, Nothing, B]
  // }

  // object Ref {
  //   def make[A]: ZIO[Any, Nothing, Ref[A]] =
  //     ???
  // }

  // Promise

  def someExpensiveComputation(n: Int): ZIO[Any, Nothing, Int] =
    ZIO.sleep(1.second) *> ZIO.succeed(n * 2)

  val inputs = List(1, 2, 3, 4, 5)

  val workflow =
    for {
      ref <- Ref.make(0)
      _   <- ZIO.foreachPar(inputs) { input =>
        someExpensiveComputation(input).flatMap { output =>
          ref.get.flatMap(old => ref.set(old + output)) // 18
          // ref.update(_ + output)  
        }  
      }
      sum <- ref.get
      _   <- ZIO.debug(sum)
    } yield ()

  val anotherWorkflow =
    for {
      promise <- Promise.make[Nothing, Unit]
      fiber   <- (ZIO.sleep(5.seconds) *> promise.succeed(())).fork
      _       <- promise.await
      _       <- fiber.interrupt
    } yield ()

  def sayHelloAndIncrementIfEven(ref: Ref[Int]): ZIO[Any, Nothing, Unit] =
    ref.modify { currentValue =>
      if (currentValue % 2 == 0)
        (ZIO.debug("Hello!"), currentValue + 1)
      else
        (ZIO.unit, currentValue)
    }.flatten

  val thirdWorkflow =
    for {
      ref <- Ref.make(0)
      _   <- sayHelloAndIncrementIfEven(ref)
      _   <- sayHelloAndIncrementIfEven(ref)
      value <- ref.get
      _     <- ZIO.debug(value)
    } yield ()

  val run =
    thirdWorkflow
}