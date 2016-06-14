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

object ChannelService extends ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)
  val dataService = DataService.default
  logger.info("STARTING THE CHANNEL SERVER")

  // all channels
  @volatile private var _channels = mutable.Map[Long, ByteChannel]()

  // all buffers.  this belongs on the audioserver
  @volatile private var _buffers = mutable.Map[Long, CircularByteBuffer]()

  private val channelWriter = new ChannelWriter()
  private val channelReader = new ChannelReader()

  private val wThread = new Thread(ChannelService.getChannelWriter())
  private val rThread = new Thread(ChannelService.getChannelReader())

  wThread.start()
  rThread.start()

  def shutdown() = {
    channelWriter.shutdown()
    channelReader.shutdown()
    wThread.join()
    rThread.join()
  }

  private def getChannelWriter() = {
    channelWriter
  }

  private def getChannelReader() = {
    channelReader
  }

  def getChannels(userId:Long) = _channels

  def getBuffers(userId:Long) = _buffers

  def login(userId:Long, channel:ByteChannel):Unit = {
    getChannels(userId) += (userId -> channel)
    getBuffers(userId) += (userId -> CircularByteBuffer.newBuf(userId.toInt))
  }

  def addUser(pipeline:AudioPipeline, roomId:Long) = {
    getChannelWriter().addUser(pipeline)
    getChannelReader().addUser(pipeline, roomId)
  }

  def logout(userId:Long):Boolean = {
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

  def getAudioPipeline(userId:Long):Option[AudioPipeline] = {
    for {
      c <- getChannels(userId).get(userId)
      b <- getBuffers(userId).get(userId)
    } yield AudioPipeline(userId, c, b)
  }

}

// TODO: Check perf differnce with using native byte buffers
class ChannelWriter() extends Runnable with ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)
  val dataService = DataService.default

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
  }
}

class ChannelReader() extends Runnable with ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)
  @volatile var running = true

  case class ChannelReaderContext(
    userId:Long,
    var view:AudioView,
    var others:List[AudioPipeline],
    var level:Int,
    var readCt:Int,
    channel:ByteChannel)

  val dataService = DataService.default

  var contexts:mutable.Map[Long, ChannelReaderContext] = mutable.Map[Long, ChannelReaderContext]()

  val baseline = Array.ofDim[Byte](bufferLengthInBytes)
  val readBytes = Array.ofDim[Byte](bufferLengthInBytes)

  def removeUser(userId:Long):Unit = {
    contexts -= userId
  }

  def addUser(aUser:AudioPipeline, roomId:Long):Unit = {
    //val userId = aUser.id
    //var view:AudioView = dataService.getAudioViewForUser(userId)

    //var readCt:Int = 0
    //var others:List[AudioPipeline] = view.people.filterKeys(_.id != userId).values
    //others.map { _.buffer.register(aUser.userId)} // register here
    //var level = others.size
    //contexts += (userId, ChannelReaderContext(userId, view, others, level, readCt, aUser.channel))
  }

  def readMultiple(context:ChannelReaderContext):Unit = {
    //val userId = context.userId
    //if(context.readCt % bufferCheck == 0) {
      //val roomId = dataService.getRoomIdForUser(userId)
      //dataService.getAudioViewForUser(userId, roomId) foreach (context.view = _)
      //context.others = context.view.people.filterKeys(_ != userId).values
      //// register ehre
      //context.others.map { _.buffer.register(userId) }
      //context.level = context.others.size
    //}

    ////view = DataService.getAudioViewForUser(userId)
    //if(context.view.people.size > 1) {
      //var sumStreams:Array[Byte] = context.others.foldLeft(baseline){ ( l,c ) =>
        //val thisStream = c.buffer.read(Some(readCt), userId)
        //if(java.util.Arrays.equals(thisStream,baseline)) {
          //l
        //} else {
          //for(i <- 0 until bufferLengthInBytes) {
            //l(i) = thisStream(i)
          //}
          //l
        //}
      //}
      //writeChannel(ByteBuffer.wrap(sumStreams), context.channel)
      //context.readCt = context.readCt + 1

    //} else {
      //logger.debug("no connections. sleeping /bl:h" + bufferLengthInBytes)
      //Thread.sleep(1*1000)
      //Thread.`yield`
    //}
  }

  def shutdown():Unit = {
    running = false
  }

  def run() = {
    while(running){
      contexts.values.foreach { readMultiple(_) }
    }
  }
}
