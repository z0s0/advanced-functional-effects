package functionaleffects

import zio._

object Streaming {

  // Why do we need streams?
  // How are streams different from functional effects

  // ZIO[R, E, A]
  // Workflow that will either fail with an E or succeed with exactly one A

  // ZIO[R, E, Chunk[A]]

  // ZIOs or other functional effects are "binary"
  // they are either "running" (and don't have any results for us right now)
  // or they are "done" (and have all the results they will ever have for us)

  // ZStream[R, E, A]

  // Workflow that either fail with an E or succeed with zero or more A values
  // potentially have infinite A values

  // Inputs (new transactions, new infor from business partners) >>>
  // Processing (take new input and produce new output) >>>
  // >>> Outputs (fraud status updates)

  // Stream as an effectual iterator

  trait Iterable[+A] {
    def iterator: Iterator[A]
  }

  trait Iterator[+A] {
    def hasNext: Boolean
    def next(): A
  }

  object Iterator {
    def make[A](): Iterator[A] =
      ???
  }

  // If I evaluate next and get an A it means "here is one A value", there
  // may or may not be more
  // If I get a None then it means iterator is done, there will definitely be
  // no more A values, don't call me again

  // Three cases:
  // 1. Got a value A
  // 2. Got an error E
  // 3. Got an end of stream signal

  trait ZIterator[-R, +E, +A] {
    def next: ZIO[R, Option[E], A]
  }

  object ZIterator {
    def make[R, E, A]: ZIO[Scope, Nothing, ZIterator[R, E, A]] =
      ???
  }

  // 1. 1000 chunks of 1 element

  // Run 1 ZIO workflow to "open" the stream
  // Run 1000 ZIO workflows to get the chunks
  // 1001 ZIO workflows

  // 2. 1 chunk of 1000 elements

  // Run 1 ZIO workflow to "open" the stream
  // Run 1 ZIO workflow to get the chunk'
  // 2 ZIO workflows

  final case class ZStream[R, E, A](
      pull: ZIO[Scope, Nothing, ZIO[R, Option[E], Chunk[A]]]
  ) {
    def map[B](f: A => B): ZStream[R, E, B] =
      ZStream(pull.map(_.map(_.map(f))))
  }

  // Streams produce their results incrementally

  // outer ZIO represents "opening" the stream
  // inner ZIO represents "pulling" the next value from the stream

  // "Chunking"

  // not purely functional interface
  // not properly handle asynchrony
  // not handle resource safety

  // LazyList in Scala -- only use that for "mathy" type operations
}

object FakeStreamsExample {
  import zio.stream._

  lazy val stream: ZStream[Any, Nothing, Int] =
    ???

  val stream2: ZStream[Any, Nothing, Int] =
    stream.scan(0)(_ + _)

  val zio2: ZIO[Any, Nothing, Int] =
    stream.runFold(0)(_ + _)

  // this won't terminate if the original stream is infinite
  // this won't produce any elements until the original stream has produced all its elements

  //   ZIO            Akka        Conceptually
  // ZStream         Source        Beginning
  // ZPipeline        Flow          Middle
  // ZSink            Sink           End

  // ZStream - producer of zero or more A values
  // Pipeline - consumes zero or more A values and emits zero or more B values
  // ZSink - consumes zero or more A values to produce a summary value

  // Inputs (new transactions, new infor from business partners) >>>
  // Processing (take new input and produce new output) >>>
  // >>> Outputs (fraud status updates)

  trait NewTransaction
  trait AnalysisResult

  // ZPipeline[R, E, A, B]

  val infoFromBusinessPartners: ZStream[Any, Nothing, NewTransaction] =
    ???

  val analysisPipeline
      : ZPipeline[Any, Nothing, NewTransaction, AnalysisResult] =
    ???

  val databaseWriter: ZSink[Any, Nothing, AnalysisResult, Nothing, Unit] =
    ???

  val loggingSink: ZSink[Any, Nothing, AnalysisResult, Nothing, Unit] =
    ???

  val myDataflow: ZIO[Any, Nothing, Unit] =
    infoFromBusinessPartners >>>
      analysisPipeline >>>
      (databaseWriter zipPar loggingSink)
}
