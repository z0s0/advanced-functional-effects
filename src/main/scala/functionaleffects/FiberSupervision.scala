package functionaleffects

import zio._

object FiberSupervision extends ZIOAppDefault {

  val workflow: URIO[Any,Fiber.Runtime[Nothing,Fiber.Runtime[Nothing,Unit]]] =
    (ZIO.sleep(5.seconds) *> ZIO.debug("Time to wake up!")).forkDaemon.forkDaemon

  val run =
    for {
      _ <- workflow
      _ <- ZIO.sleep(10.seconds)
    } yield ()

  def zipPar[R, E, A, B](left: ZIO[R, E, A], right: ZIO[R, E, B]): ZIO[R, E, (A, B)] =
    for {
      leftFiber <- left.fork
      rightFiber <- right.fork
      leftResult <- leftFiber.join
      rightResult <- rightFiber.join
    } yield (leftResult, rightResult)

  // Wake up is never printed!

  // ZIO has a "fiber supervision" model (structured concurrency)
  // When a fiber completes execution before completing wind down it will
  // interrupt all of its children

  // forkDaemon forks a fiber "without supervision"
  // If we want to create a true "background process" we use forkDaemon

  // Cats Effect or Monix do not have this

  // They don't have a concept of fiber supervision

  // Cats Effect start === ZIO forkDaemon
}