object Channels {
  import zio.Chunk

  def myMethodIHaventImplemented =
    ???

  type ??? = Nothing

  trait ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]


  type ZSink[-R, +E, -In, +L, +Z] = ZChannel[R, ???, Chunk[In], Any, E, Chunk[L], Z]
  type ZPipeline[-Env, +Err, -In, +Out] = ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Any]

  type ZSinkErr[-R, +E, -InErr, -In, +L, +Z] = ZChannel[R, InErr, Chunk[In], Any, E, Chunk[L], Z]

  type ZStream[-R, +E, +A] = ZChannel[R, Any, Any, Any, E, Chunk[A], Any]

  // Could be implemented this way
  // Not actually implemented due to order of dependencies and performance / complexity
  type ZIO[-R, +E, +A]     = ZChannel[R, Any, Any, Any, E, Nothing, A]

  // type InfinitePipeline[-Env, +Err, -In, +Out] = ZChannel[Env, Nothing, Chunk[In], Any, Err, Chunk[Out], Nothing]
}