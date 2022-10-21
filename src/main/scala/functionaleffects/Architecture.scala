package functionaleffects

import zio._

import java.io.IOException

object Architecture {


}

sealed trait Github {
  def addComment(repo: String, issue: Int, comment: String): IO[Nothing, Unit]
}

object Github {

  def addComment(repo: String, issue: Int, comment: String): ZIO[Github, Nothing, Unit] =
    ZIO.serviceWithZIO(_.addComment(repo, issue, comment))

  final case class GithubLive(http: Http, semaphore: Semaphore) extends Github {
    def addComment(repo: String, issue: Int, comment: String): IO[Nothing, Unit] =
      semaphore.withPermit {
        ???
      }
  }

  val live: ZLayer[Http & Config, Nothing, Github] =
    ZLayer {
      for {
        config    <- ZIO.service[Config]
        http      <- ZIO.service[Http]
        semaphore <- Semaphore.make(config.desiredParallelism)
      } yield GithubLive(http, semaphore)
    }
}

trait Config {
  def desiredParallelism: Int
}

object Config {

  final case class ConfigLive(desiredParallelism: Int) extends Config

  val default: ZLayer[Any, Nothing, Config] =
    ZLayer.succeed(ConfigLive(42))
}

trait Http {
  def get(url: String): IO[IOException, String]
  def post(url: String, body: String): IO[IOException, String]
}

object Http {

  final case class HttpLive() extends Http {
    def get(url: String): IO[IOException, String] =
      ???
    def post(url: String, body: String): IO[IOException, String] =
      ???
  }

  val live: ZLayer[Any, Nothing, Http] =
    ZLayer.succeed(HttpLive())
}

final case class HttpTest() extends Http {
  def get(url: String): IO[IOException, String] =
    ???
  def post(url: String, body: String): IO[IOException, String] =
    ???
}

trait UserService

final case class FacebookService() extends UserService
final case class TwitterService() extends UserService

object UserServiceExample {

  val zio: ZIO[FacebookService & TwitterService, Nothing, Unit] =
    ???
}

object Main extends ZIOAppDefault {

  val applicationLogic =
    for {
      _ <- Github.addComment("zio/zio", 1, "Hello, world!")
    } yield ()

  val run =
    applicationLogic.provide(
      githubSubcomponent
    )

  val githubSubcomponent: ZLayer[Any, Nothing, Github] =
    ZLayer.make[Github](
      Github.live,
      Http.live,
      Config.default
    )
}