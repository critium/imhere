package together.audio

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels._

import together.util._
import together.data._
import together.audio.Conversions._

import scala.collection.mutable
import scala.util._

import org.slf4j.LoggerFactory

trait ChannelServiceTrait {
  def shutdown()
  def tick
  def getChannels(userId:Long):mutable.Map[Long, ByteChannel]
  def getBuffers(userId:Long):mutable.Map[Long, CircularByteBuffer]
  def login(audioLogin:AudioLogin, channel:ByteChannel):Try[AudioLogin]
  def addUser(pipeline:AudioPipeline, roomId:Long)
  def logoutAudio(userId:Long):Boolean
  def getOtherPipelinesInRoom(userId:Long):List[AudioPipeline]
  def tap
}

class ChannelServiceImpl(debug:Boolean) extends ChannelServiceTrait with ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  val dataService = ServiceLocator.dataService
  //val dataService = DataService.default
  logger.info("STARTING THE CHANNEL SERVER")

  // all channels
  @volatile private var _channels = mutable.Map[Long, ByteChannel]()

  // all buffers.  this belongs on the audioserver
  @volatile private var _buffers = mutable.Map[Long, CircularByteBuffer]()

  private val channelWriter = new ChannelWriter(dataService)
  private val channelReader = new ChannelReader(dataService, this)

  private val wThread = new Thread(getChannelWriter())
  private val rThread = new Thread(getChannelReader())

  if(!debug) {
    wThread.start()
    rThread.start()
  }

  override def shutdown() = {
    channelWriter.shutdown()
    channelReader.shutdown()

    if(!debug){
      wThread.join()
      rThread.join()
    }
  }

  private def getChannelWriter() = {
    channelWriter
  }

  private def getChannelReader() = {
    channelReader
  }

  override def getChannels(userId:Long) = _channels

  override def getBuffers(userId:Long) = _buffers

  override def login(audioLogin:AudioLogin, channel:ByteChannel):Try[AudioLogin] = Try {
    val userId = audioLogin.userId
    val buffer = CircularByteBuffer.newBuf(userId.toInt)
    getChannels(userId) += (userId -> channel)
    getBuffers(userId) += (userId -> buffer)

    addUser(AudioPipeline(userId,channel,buffer), lobbyRoomId)

    audioLogin
  }

  override def addUser(pipeline:AudioPipeline, roomId:Long) = {
    getChannelWriter().addUser(pipeline)
    getChannelReader().addUser(pipeline, roomId)
  }

  override def logoutAudio(userId:Long):Boolean = {
    Try{
      getChannelWriter().removeUser(userId)
      getChannelReader().removeUser(userId)
      getChannels(userId) -= userId
      getBuffers(userId) -= userId
    } match {
      case Success(v) => true
      case Failure(e) =>
        e.printStackTrace
        false
    }

  }

  override def getOtherPipelinesInRoom(userId:Long):List[AudioPipeline] = {
    dataService.getOthersInRoom(userId).map { otherUserId =>
      getAudioPipeline(otherUserId)
    }.flatten
  }

  private def getAudioPipeline(userId:Long):Option[AudioPipeline] = {
    for {
      c <- getChannels(userId).get(userId)
      b <- getBuffers(userId).get(userId)
    } yield AudioPipeline(userId, c, b)
  }

  override def tick = if(debug) {
    channelWriter.tick
    channelReader.tick
  }

  override def tap = {
    channelWriter.tap
  }

  logger.info("STARTING THE CHANNEL SERVER")

}

// TODO: Check perf differnce with using native byte buffers
class ChannelWriter(dataService:DataServiceTrait) extends Runnable with ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  @volatile var users:mutable.Map[Long, AudioPipeline] = mutable.Map[Long, AudioPipeline]()
  @volatile var running = true

  val baseline = ByteBuffer.allocate(bufferLengthInBytes)
  val readBuf = ByteBuffer.allocate(bufferLengthInBytes)

  def addUser(aUser:AudioPipeline):Unit = {
    users += (aUser.id -> aUser)
  }

  def removeUser(userId:Long):Unit = {
    users -= userId
  }

  def writeMultiple(aUser:AudioPipeline) = {
    readChannel(bufferLengthInBytes, aUser.channel, Some(readBuf))
    aUser.buffer.write(readBuf.array)
    readBuf.clear()
  }

  def shutdown():Unit = {
    running = false
  }

  def run() = {
    while(running) {
      users.values.foreach( writeMultiple(_) )
    }
    logger.debug("SHUTTING DOWN WRITER")
  }

  def tick = {
    users.values.foreach( writeMultiple(_) )
  }

  def tap:Unit = {
    users foreach { case (k,p) =>
      p.buffer.tap(k)
    }
  }
}

class ChannelReader(dataService:DataServiceTrait, channelService:ChannelServiceTrait) extends Runnable with ChannelSupport {

  private val logger = LoggerFactory.getLogger(getClass)
  @volatile var running = true

  case class ChannelReaderContext(
    userId:Long,
    var view:List[AudioView],
    var others:List[AudioPipeline],
    var level:Int,
    val readCt:mutable.Map[Long, Int],
    channel:ByteChannel) {
      def pos(userId:Long):Int = readCt.get(userId).getOrElse(0)
    }

  var contexts:mutable.Map[Long, ChannelReaderContext] = mutable.Map[Long, ChannelReaderContext]()

  val baseline = Array.ofDim[Byte](bufferLengthInBytes)
  val readBytes = Array.ofDim[Byte](bufferLengthInBytes)

  def removeUser(userId:Long):Unit = {
    contexts -= userId
  }

  def addUser(aUser:AudioPipeline, roomId:Long):Unit = {
    val userId = aUser.id
    var view:List[AudioView] = dataService.getAudioViewForUser(userId)
    var others:List[AudioPipeline] = channelService.getOtherPipelinesInRoom(userId)
    var level = others.size
    val readCt = mutable.Map[Long,Int]();
    contexts += (userId -> ChannelReaderContext(userId, view, others, level, readCt, aUser.channel))
  }

  def readMultiple(context:ChannelReaderContext):Unit = {
    val userId = context.userId
    if(context.pos(userId) % bufferCheck == 0) {
      val roomId = dataService.getRoomIdForUser(userId)
      context.others = channelService.getOtherPipelinesInRoom(userId)
      context.level = context.others.size
      context.view = dataService.getAudioViewForUser(userId)
    }

    ////view = DataService.getAudioViewForUser(userId)
    if(context.view.size > 1) {
      var sumStreams:Array[Byte] = context.others.foldLeft(baseline){ ( l,c ) =>
        val (newPos, thisStream) = c.buffer.read(Some(context.pos(userId)), userId)
        context.readCt += (userId -> newPos)
        if(java.util.Arrays.equals(thisStream,baseline)) {
          l
        } else {
          for(i <- 0 until bufferLengthInBytes) {
            l(i) = thisStream(i)
          }
          l
        }
      }

      writeChannel(ByteBuffer.wrap(sumStreams), context.channel)

    } else {
      logger.debug("no connections. sleeping /bl:h" + bufferLengthInBytes)
      Thread.sleep(1*1000)
      Thread.`yield`
    }
  }

  def shutdown():Unit = {
    running = false
  }

  def run() = {
    while(running){
      contexts.values.foreach { readMultiple(_) }
    }
    logger.debug("SHUTTING DOWN READER")
  }

  def tick = {
    contexts.values.foreach { readMultiple(_) }
  }
}
