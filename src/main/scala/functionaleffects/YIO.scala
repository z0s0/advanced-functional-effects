package functionaleffects

import functionaleffects.YIO.FlatMap

trait YIO[+A] { self =>
  import YIO._

  def *>[B](that: YIO[B]): YIO[B] = zipRight(that)

  def repeatN(n: Int): YIO[Unit] =
    if (n <= 0) succeed(())
    else self *> repeatN(n - 1)

  def unsafeRun(): Unit = ???

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
  def unsafeRun: A =
    ???
}

final case class RuntimeFiber[+A](yio: YIO[A]) extends Fiber[A] {
  def unsafeRun: A = ???
}
object YIO {
  def succeed[A](value: => A): YIO[A] = Succeed(() => value)

  val unit: YIO[Unit] = succeed(())

  def main(args: Array[String]): Unit = {
    YIO.succeed(println("12")).repeatN(4).unsafeRun
  }

  final case class Succeed[A](value: () => A) extends YIO[A]
  final case class FlatMap[A, B](first: YIO[A], andThen: A => YIO[B])
      extends YIO[B]
}
