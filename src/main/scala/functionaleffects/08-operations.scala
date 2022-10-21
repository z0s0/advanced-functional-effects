/**
 * ZIO provides features specifically designed to improve your experience
 * deploying, scaling, monitoring, and troubleshooting ZIO applications.
 * These features include async stack traces, fiber dumps, logging hooks,
 * and integrated metrics and monitoring.
 *
 * In this section, you get to explore the operational side of ZIO.
 */
package advancedfunctionaleffects.ops

import zio._

import zio.test._
import zio.test.TestAspect._

object TracesExample extends ZIOAppDefault {

  val run =
    ZIO.fail("fail")
}

object AsyncTraces extends ZIOSpecDefault {
  def spec =
    suite("AsyncTraces") {

      /**
       * EXERCISE
       *
       * Pull out the `traces` associated with the following sandboxed
       * failure, and verify there is at least one trace element.
       */
      test("traces") {
        def async =
          for {
            _ <- ZIO.sleep(1.millis)
            _ <- ZIO.fail("Uh oh!")
          } yield ()

        def traces(cause: Cause[String]): List[StackTrace] =
          cause.traces


        Live.live(for {
          cause <- async.sandbox.flip
          ts    = traces(cause)
          _     <- ZIO.debug(cause.prettyPrint)
        } yield assertTrue(ts(0).stackTrace.length > 0))
      }
    }
}

// Exception in thread "zio-fiber-24" java.lang.String: Uh oh!
//         at advancedfunctionaleffects.ops.AsyncTraces.spec.async(08-operations.scala:30)
//         at advancedfunctionaleffects.ops.AsyncTraces.spec(08-operations.scala:38)
//         at advancedfunctionaleffects.ops.AsyncTraces.spec(08-operations.scala:37)
//         at advancedfunctionaleffects.ops.AsyncTraces.spec(08-operations.scala:26)

object FiberDumps extends ZIOSpecDefault {
  def spec =
    suite("FiberDumps") {

      /**
       * EXERCISE
       *
       * Compute and print out all fiber dumps of the fibers running in this test.
       */
      test("dump") {
        val example =
          for {
            promise <- Promise.make[Nothing, Unit]
            blocked <- promise.await.forkDaemon
            child1  <- ZIO.foreach(1 to 100000)(_ => ZIO.unit).forkDaemon
          } yield ()

        for {
          supervisor <- Supervisor.track(false)
          _          <- example.supervised(supervisor)
          children   <- supervisor.value
          _          <- ZIO.foreach(children)(child => child.dump.flatMap(dump => dump.prettyPrint.debug))
          _          <- ZIO.debug("*****")
          _          <- Fiber.dumpAll
        } yield assertTrue(children.length == 2)
      } @@ flaky
    }

// "zio-fiber-74" (5ms) 
//         Status: Running((Interruption, CooperativeYielding, FiberRoots), <no trace>)
//         at advancedfunctionaleffects.ops.FiberDumps.spec.example(08-operations.scala:72)


// "zio-fiber-73" (12ms) waiting on #24
//         Status: Suspended((Interruption, CooperativeYielding, FiberRoots), advancedfunctionaleffects.ops.FiberDumps.spec.example(08-operations.scala:71))
//         at advancedfunctionaleffects.ops.FiberDumps.spec.example(08-operations.scala:71)
}

// ZIO provides "front end"
// Consistent interface that you use to specify your intent to log something
// or to record some metric (ZIO Core)
// Specialized libraries are going to provide specific backend implementations
// that do something with that information that you wanted to log / record as a
// metric, etc...
// ZIO Logging (log4j, logback, )
// ZIO Metrics Connectors (Prometheus, Datadog, etc...)
// Install backends as layers

// val prometheusMetricsBackend: ZLayer[Any, Nothing, Unit] = ???

// logging front end in ZIO
  // Log statements
  // Log levels
  // Log spans
  // Log annotations

object LoggingExample extends ZIOAppDefault {

  val loudLogger =
    ZLogger.default.map(_.toUpperCase).map(println)

  override val bootstrap =
    Runtime.removeDefaultLoggers ++ Runtime.addLogger(loudLogger)

  val run =
    ZIO.logAnnotate("key", "value") {
      ZIO.logSpan("my span") {
        ZIO.logSpan("another span") {
          ZIO.logLevel(LogLevel.Error) {
            ZIO.log("Hello from a logger!") *>
              ZIO.logLevel(LogLevel.Info) {
                ZIO.log("This is another log")
              }
          }
        }
      }
    }
}

// timestamp=2022-10-21T10:00:31.939864Z level=INFO thread=#zio-fiber-6 message="Hello from a logger!" location=advancedfunctionaleffects.ops.LoggingExample.run file=08-operations.scala line=117

import zio.metrics.Metric

// Metrics
// ZIO Core defines a "common language" for how we specify that we want to track
// something as a metric
// ZIO Metrics Connectors provides the specific backend implementations for
// Prometheus, Datadog, StatsD

object Logging extends ZIOSpecDefault {
  def spec =
    suite("Logging")()
}

object MetricsExample extends ZIOAppDefault {

  val metric = Metric.counter("my-counter").tagged("key", "value")

  val trackSuccesses =
    metric.trackSuccessWith[Any](_ => 1L)

  val executionTime =
    Metric.timer("my-timer", java.time.temporal.ChronoUnit.SECONDS)

  val trackExecutionTime =
    executionTime.trackDuration

  val workflow =
    ZIO.debug("Hello")

  val workflowWithMetrics =
    workflow @@ trackSuccesses


  val run =
    for {
      state <- metric.value
      _     <- ZIO.debug(state)
      _     <- metric.increment
      state <- metric.value
      _     <- ZIO.debug(state)
      _     <- workflowWithMetrics
      _     <- workflowWithMetrics
      state <- metric.value
      _     <- ZIO.debug(state)
    } yield ()
}

object Metrics extends ZIOSpecDefault {
  def spec =
    suite("Metrics")()
}
