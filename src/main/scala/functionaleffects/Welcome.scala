package functionaleffects

import zio._

import scala.concurrent._
import scala.concurrent.ExecutionContext.global

object Welcome extends ZIOAppDefault {

  // Functional effects are just descriptions of programs
  //   Execution plans - ZIO / IO / Task
  //   Running computation - Fiber

  val sayHello: ZIO[Any, Nothing, Unit] =
    ZIO.succeed(println("Hello Evolution!"))

  val sayHelloFiveTimes =
    sayHello.repeatN(4)

  val myWorkflow = {
    ZIO.succeed(println("My log statement")) *>
      ZIO.succeed(42)
  }

  val myResourcefulWorkflow =
    ZIO.acquireReleaseWith {
      ZIO.debug("Acquired resource")
    } { _ =>
      ZIO.debug("Releasing resource")
    } { _ =>
      ZIO.debug("Using resource") *> ZIO.sleep(10.seconds)
    }

  val zio =
    for {
      fiber <- myResourcefulWorkflow.fork
      _ <- ZIO.sleep(1.second)
      _ <- fiber.interrupt
    } yield ()

  // Acquired resource
  // Using resource
  // Releasing resource

  val run =
    for {
      fiber <- ZIO.succeed(while (true) { println("won't stop") }).fork
      _ <- fiber.interrupt
    } yield ()
}
