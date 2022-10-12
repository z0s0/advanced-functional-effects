package functionaleffects

import scala.concurrent.duration._

// ZIO[R, E, A]
// R == services required
// E == how the workflow can fail
// A == how the workflow can succeed

// CIO[A] = ZIO[Any, Throwable, A]
// Task[A] = ZIO[Any, Throwable, A]

object ErrorExample {
  import zio._

  val maybeThrows: ZIO[Any, Throwable, Int] =
    ZIO.attempt(42 / 0)

  // Fail
  // Die - Defects

  val liar: ZIO[Any, Nothing, Int] =
    ZIO.succeed {
      throw new Exception("I lied!")
    }

  sealed trait MyDomainError

  object MyDomainError {
    case object ServiceNotAvailable extends MyDomainError
    case object InvalidAuthentication extends MyDomainError
  }

  val myServiceRequest: ZIO[Any, MyDomainError, String] =
    ???

  val recovered: ZIO[Any, Nothing, Int] =
    maybeThrows.catchAll(n => ZIO.succeed(-1))
}

trait YIO[+A] { self =>
  import YIO._

  def *>[B](that: YIO[B]): YIO[B] =
    zipRight(that)

  def flatMap[B](f: A => YIO[B]): YIO[B] =
    FlatMap(self, f)

  def fork: YIO[Fiber[A]] =
    YIO.succeed {
      val fiber = RuntimeFiber(self)
      fiber.start()
      fiber
    }

  def map[B](f: A => B): YIO[B] =
    flatMap(a => succeed(f(a)))

  def repeatN(n: Int): YIO[Unit] =
    if (n <= 0) unit
    else self *> repeatN(n - 1)

  def unsafeRunAsync(): Unit = {
    val fiber = RuntimeFiber(self)
    fiber.start()
  }

  def unsafeRunSync(): A = {
    var result: A = null.asInstanceOf[A]
    val latch = new java.util.concurrent.CountDownLatch(1)
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

  def zipRight[B](that: YIO[B]): YIO[B] =
    zipWith(that)((_, b) => b)

  def zipWith[B, C](that: YIO[B])(f: (A, B) => C): YIO[C] =
    for {
      a <- self
      b <- that
    } yield f(a, b)

  def zipWithPar[B, C](that: YIO[B])(f: (A, B) => C): YIO[C] =
    for {
      left <- self.fork
      right <- that.fork
      a <- left.join
      b <- right.join
    } yield f(a, b)

}

object YIO {

  def async[A](register: (YIO[A] => Unit) => Any): YIO[A] =
    Async(register)

  private lazy val scheduler: java.util.concurrent.ScheduledExecutorService = {
    java.util.concurrent.Executors
      .newSingleThreadScheduledExecutor { (r: Runnable) =>
        val t = new Thread(r)
        t.setDaemon(true)
        t
      }
  }

  import scala.concurrent.duration.FiniteDuration

  def sleep(duration: FiniteDuration): YIO[Unit] =
    YIO.async { cb =>
      val schedule =
        scheduler.schedule(
          (() => cb(YIO.unit)): Runnable,
          duration.length,
          duration.unit
        )
    }

  def collectAll[A](yios: List[YIO[A]]): YIO[List[A]] =
    yios.foldRight(YIO.succeed(List.empty[A])) { case (yio, result) =>
      result.zipWith(yio)((list, a) => a :: list)
    }

  def collectAllPar[A](yios: List[YIO[A]]): YIO[List[A]] =
    collectAll(yios.map(_.fork)).flatMap { fibers =>
      collectAll(fibers.map(_.join))
    }

  def foreach[A, B](as: List[A])(f: A => YIO[B]): YIO[List[B]] =
    collectAll(as.map(f))

  def foreachPar[A, B](as: List[A])(f: A => YIO[B]): YIO[List[B]] =
    collectAllPar(as.map(f))

  val never: YIO[Nothing] =
    async(_ => ())

  def succeed[A](value: => A): YIO[A] =
    Succeed(() => value)

  val unit: YIO[Unit] =
    succeed(())

  final case class Async[A](register: (YIO[A] => Unit) => Any) extends YIO[A]
  final case class FlatMap[A, B](first: YIO[A], andThen: A => YIO[B])
      extends YIO[B]
  final case class Succeed[A](value: () => A) extends YIO[A]
}

trait Fiber[+A] {
  def join: YIO[A]
}

final case class RuntimeFiber[A](yio: YIO[A]) extends Fiber[A] {

  val executor = scala.concurrent.ExecutionContext.global

  type ErasedYIO = YIO[Any]
  type ErasedContinuation = Any => YIO[Any]

  private var currentYIO: ErasedYIO = yio
  private val stack = scala.collection.mutable.Stack[ErasedContinuation]()

  private val running =
    new java.util.concurrent.atomic.AtomicBoolean(false)

  private val inbox =
    new java.util.concurrent.ConcurrentLinkedQueue[FiberMessage]

  private[this] val observers =
    scala.collection.mutable.Set[YIO[Any] => Unit]()

  private[this] var exit: A =
    null.asInstanceOf[A]

  def offerToInbox(fiberMessage: FiberMessage): Unit = {
    inbox.offer(fiberMessage)
    if (running.compareAndSet(false, true)) {
      drainQueueOnExecutor()
    }
  }

  def drainQueueOnExecutor(): Unit =
    executor.execute(() => drainQueueOnCurrentThread)

  @annotation.tailrec
  def drainQueueOnCurrentThread(): Unit = {
    var fiberMessage = inbox.poll()
    while (fiberMessage != null) {
      processFiberMessage(fiberMessage)
      fiberMessage = inbox.poll()
    }
    running.set(false)
    if (!inbox.isEmpty) {
      if (running.compareAndSet(false, true)) {
        drainQueueOnCurrentThread()
      }
    }
  }

  def processFiberMessage(fiberMessage: FiberMessage): Unit =
    fiberMessage match {
      case FiberMessage.Resume(yio) =>
        currentYIO = yio
        runLoop()
      case FiberMessage.Start =>
        runLoop()
      case FiberMessage.AddObserver(observer) =>
        if (exit == null) {
          observers.add(observer)
        } else {
          observer(YIO.succeed(exit))
        }
    }

  def start(): Unit =
    offerToInbox(FiberMessage.Start)

  def join: YIO[A] =
    YIO.async(cb =>
      offerToInbox(FiberMessage.AddObserver(cb.asInstanceOf[YIO[Any] => Unit]))
    )

  def runLoop(): Unit = {
    var loop = true
    while (loop) {
      currentYIO match {
        case YIO.Succeed(value) =>
          val computedValue = value()
          if (stack.isEmpty) {
            exit = computedValue.asInstanceOf[A]
            observers.foreach(_(YIO.succeed(computedValue)))
            loop = false
          } else {
            val nextContinuation = stack.pop()
            currentYIO = nextContinuation(computedValue)
          }
        case YIO.FlatMap(first, andThen) =>
          currentYIO = first
          stack.push(andThen)
        case YIO.Async(register) =>
          currentYIO = null
          loop = false
          register(yio => offerToInbox(FiberMessage.Resume(yio)))
      }
    }
  }
}

sealed trait FiberMessage

object FiberMessage {
  final case class AddObserver(cb: YIO[Any] => Unit) extends FiberMessage
  final case class Resume(yio: YIO[Any]) extends FiberMessage
  case object Start extends FiberMessage
}

sealed trait Promise[A] {
  def await: YIO[A]
  def succeed(a: A): YIO[Boolean]
}

object Promise {

  def make[A]: YIO[Promise[A]] =
    YIO.succeed {
      val state =
        new java.util.concurrent.atomic.AtomicReference[State[A]](Empty(Nil))
      new Promise[A] {
        def await: YIO[A] =
          YIO.async { cb =>
            var loop = true
            while (loop) {
              val currentState = state.get
              currentState match {
                case Done(a) =>
                  loop = false
                  cb(YIO.succeed(a))
                case Empty(callbacks) =>
                  if (state.compareAndSet(currentState, Empty(cb :: callbacks))) {
                    loop = false
                  }
              }
            }
          }
        def succeed(a: A): YIO[Boolean] =
          YIO.succeed {
            var loop = true
            var result = false
            while (loop) {
              val currentState = state.get
              currentState match {
                case Done(_) =>
                  loop = false
                case Empty(joiners) =>
                  if (state.compareAndSet(currentState, Done(a))) {
                    joiners.foreach(_(YIO.succeed(a)))
                    result = true
                    loop = false
                  }
              }
            }
            result
          }
      }
    }

  sealed trait State[A]

  final case class Done[A](value: A) extends State[A]
  final case class Empty[A](joiners: List[YIO[A] => Unit]) extends State[A]
}

object Example extends App {

  val sayHello =
    YIO.succeed(println("Hello Evolution!"))

  val sayHelloFiveTimes =
    sayHello.repeatN(5)

  val left =
    YIO.succeed {
      Thread.sleep(5000)
      "left"
    }

  val right =
    YIO.succeed {
      Thread.sleep(5000)
      "right"
    }

  val parallel =
    left.zipWithPar(right)((left, right) => (left, right)).flatMap { out =>
      YIO.succeed(println(out))
    }

  val sleepExample =
    YIO.sleep(5.seconds)

  trait Runtime {
    def unsafeRun[A](yio: YIO[A]): A
  }

  sleepExample.unsafeRunSync()
}
