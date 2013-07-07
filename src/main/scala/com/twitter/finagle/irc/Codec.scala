object IrcCodec extends CodecFactory[Message, Response] {
  val server = Function.const {
    new Codec[Message, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline = {
          val pipeline = Channels.pipeline()
          pipeline.addLast("handler", new IrcHandler)
          pipeline
        }
      }
    }
  }

  val client = Function.const {
    new Codec[Message, Response] {
      def pipelineFactory = new ChannelPipelineFactory {
        def getPipeline = {
          val pipeline = Channels.pipeline()
          pipeline.addLast("handler", new IrcHandler)
          pipeline
        }
      }
    }
  }
}

