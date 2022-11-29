package functionaleffects

import zio.ZIO

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

sealed trait YIO[+A] { self =>
  import YIO._

  def *>[B](that: YIO[B]): YIO[B] = zipRight(that)

  def repeatN(n: Int): YIO[Unit] =
    if (n <= 0) succeed(())
    else self *> repeatN(n - 1)

  def unsafeRunAsync(): Unit = {
    val fiber = RuntimeFiber(self)
    fiber.start()
  }

  def unsafeRunSync(): A = {
    var result = null.asInstanceOf[A]
    val latch = new CountDownLatch(1)

    self
      .flatMap { a =>
        YIO.succeed {
          result = a
          latch.countDown()
        }
      }
      .unsafeRunAsync()

    latch.await()
    result
  }

  def flatMap[B](f: A => YIO[B]): YIO[B] =
    FlatMap(self, f)

  def map[B](f: A => B): YIO[B] =
    flatMap(a => succeed(f(a)))

  def zipWith[B, C](that: YIO[B])(f: (A, B) => C): YIO[C] =
    for {
      a <- self
      b <- that
    } yield f(a, b)

  def zipRight[B](that: YIO[B]): YIO[B] =
    zipWith(that)((_, b) => b)
}

trait Fiber[+A] {
  def start(): Unit
}

final case class RuntimeFiber[A](yio: YIO[A]) extends Fiber[A] {
  type ErasedContinuation = Any => YIO[Any]
  type ErasedYIO = YIO[Any]

  val executor = scala.concurrent.ExecutionContext.global

  private var currentYIO: ErasedYIO = yio
  private val stack = scala.collection.mutable.Stack[ErasedContinuation]()
  private val inbox = new java.util.concurrent.ConcurrentLinkedDeque[Any]()
  private val running: AtomicBoolean = new AtomicBoolean(false)

  def offerToInbox(fiberMessage: FiberMessage): Unit = {
    inbox.offer(fiberMessage)
    if (running.compareAndSet(false, true)) {
      runLoop()
    }
  }

  def drainQueueOnCurrentThread(): Unit = {
    val fiberMessage = inbox.poll()

  }
  def start(): Unit = ???

  def runLoop(): Unit = {
    var loop = true
    var result: A = null.asInstanceOf[A]
    while (loop) {
      currentYIO match {
        case YIO.Succeed(value) =>
          val computedValue = value()
          if (stack.isEmpty) {
            result = computedValue.asInstanceOf[A]
            loop = false
          } else {
            val nextCont = stack.pop()
            currentYIO = nextCont(computedValue)
          }

        case YIO.FlatMap(first, andThen) =>
          currentYIO = first
          stack.push(andThen)

        case YIO.Async(register) =>
          currentYIO = null
          var loop = false

      }
    }

    result
  }
}

sealed trait FiberMessage {
  final case class Resume[A](yio: YIO[A]) extends FiberMessage
}
object YIO {
  def succeed[A](value: => A): YIO[A] = Succeed(() => value)

  def async[A](register: (YIO[A] => Unit) => Any): YIO[A] = ???
  val unit: YIO[Unit] = succeed(())

  def main(args: Array[String]): Unit = {
//    async { cb => }
  }

  final case class Succeed[A](value: () => A) extends YIO[A]
  final case class FlatMap[A, B](first: YIO[A], andThen: A => YIO[B])
      extends YIO[B]
  final case class Async[A](register: (YIO[A] => Unit) => Any) extends YIO[A]
}
