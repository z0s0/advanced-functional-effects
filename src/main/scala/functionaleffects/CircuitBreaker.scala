package functionaleffects

import zio._

import scala.collection.immutable.{Queue => ScalaQueue}

import java.time.Instant

trait CircuitBreaker {
  def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Option[E], A]
}

object CircuitBreaker {

  def make(
      rate: Double,
      window: Duration
  ): UIO[CircuitBreaker] =
    for {
      ref <- Ref.make(State.empty)
    } yield new CircuitBreaker {
      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Option[E], A] =
        isOpen.flatMap { open =>
          if (open) zio.asSomeError.onExit(updateState)
          else ZIO.fail(None)
        }
      def isOpen: UIO[Boolean] =
        Clock.instant.flatMap { now =>
          ref.modify { state =>
            val updatedState = state.window(now, window)
            (updatedState.isOpen(rate), updatedState)
          }
        }
      def updateState(exit: Exit[Any, Any]): UIO[Unit] =
        Clock.instant.flatMap { now =>
          ref.update { state =>
            val updatedState = state.window(now, window)
            val timeStampedResult =
              exit.foldExit(
                _ => TimeStamped(Result.Failure, now),
                _ => TimeStamped(Result.Success, now)
              )
            updatedState.update(timeStampedResult)
          }
        }
    }

  sealed trait Result { self =>
    def isFailure: Boolean =
      self match {
        case Result.Failure => true
        case Result.Success => false
      }
  }

  object Result {
    case object Failure extends Result
    case object Success extends Result
  }

  private final case class State(
      timeStampedResults: ScalaQueue[TimeStamped[Result]]
  ) {
    def isOpen(rate: Double): Boolean =
      timeStampedResults.isEmpty ||
        timeStampedResults
          .count(_.value.isFailure)
          .toDouble / timeStampedResults.size <= rate
    def update(timeStampedResult: TimeStamped[Result]): State =
      State(timeStampedResults.enqueue(timeStampedResult))
    def window(now: Instant, size: Duration): State =
      State(timeStampedResults.dropWhile(_.time.isBefore(now.minus(size))))

  }

  private object State {
    val empty: State =
      State(ScalaQueue.empty)
  }

  private final case class TimeStamped[+A](value: A, time: Instant)
}
