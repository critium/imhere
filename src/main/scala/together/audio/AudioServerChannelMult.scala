package together.audio

import together.data._
import together.audio.Conversions._

import java.io._
import java.net._
import java.nio._
import java.nio.channels._

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.util._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import com.sun.media.sound._

import org.slf4j.LoggerFactory

object AudioServerChannelMult extends ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  logger.info("STARTING THE CHANNEL SERVER")

  // TODO: Check perf differnce with using native byte buffers
  class ChannelWriter() extends Runnable {
    var users:mutable.MutableList[AudioUser] = mutable.MutableList[AudioUser]()

    val baseline = ByteBuffer.allocate(bufferLengthInBytes)
    val readBuf = ByteBuffer.allocate(bufferLengthInBytes)

    def addUser(auser:AudioUser):Unit = {
      users += auser
    }

    def writeMultiple(aUser:AudioUser) = {
      readChannel(bufferLengthInBytes, aUser.channel.get, Some(readBuf))
      //val readBuf = readChannel(bufferLengthInBytes, aUser.channel.get)
      aUser.buf.write(readBuf.array)
      readBuf.clear()
    }

    def run() = {
      while(true) {
        users.foreach( writeMultiple(_) )
      }
    }
  }

  class ChannelReader() extends Runnable {
    case class ChannelReaderContext(userId:Long, var view:AudioView, var others:List[AudioUser], var level:Int, var readCt:Int, channel:SocketChannel)

    var contexts:mutable.MutableList[ChannelReaderContext] = mutable.MutableList[ChannelReaderContext]()

    val baseline = Array.ofDim[Byte](bufferLengthInBytes)
    val readBytes = Array.ofDim[Byte](bufferLengthInBytes)

    def addUser(aUser:AudioUser):Unit = {
      val userId = aUser.userId
      var view:AudioView = DataService.getAudioViewForUser(userId)

      var readCt:Int = 0
      var others:List[AudioUser] = view.people.filter(_.userId != userId)
      others.map { _.buf.register(view.userId)} // register here
      var level = others.size
      aUser.channel.map{c =>
        contexts += ChannelReaderContext(userId, view, others, level, readCt, c)
      }
    }

    def readMultiple(context:ChannelReaderContext):Unit = {
      val userId = context.userId
      if(context.readCt % bufferCheck == 0) {
        context.view = DataService.getAudioViewForUser(userId)
        context.others = context.view.people.filter(_.userId != userId)
        // register ehre
        context.others.map { _.buf.register(context.view.userId) }
        context.level = context.others.size
      }

      //view = DataService.getAudioViewForUser(userId)
      if(context.view.people.size > 1) {
        var sumStreams:Array[Byte] = context.others.foldLeft(baseline){ ( l,c ) =>
          val thisStream = c.buf.read(userId)
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
        context.readCt = context.readCt + 1

      } else {
        logger.debug("no connections. sleeping /bl:h" + bufferLengthInBytes)
        Thread.sleep(1*1000)
        Thread.`yield`
      }
    }

    def run() = {
      while(true){
        contexts.foreach { readMultiple(_) }
      }
    }
  }

  object RelayServerChanMult {
    var port = 0
    var host = ""
    var ip = ""

    @volatile var run = true

    class Relay(incPort:Int, clientHost:String) {
      port = incPort
      host = clientHost

      DataService.registerServer(AudioServerInfo(ip, host, port))

      val serverSocketChannel:ServerSocketChannel = ServerSocketChannel.open();

      serverSocketChannel.bind(new InetSocketAddress(port))

      //serverSocketChannel.socket().bind(new InetSocketAddress(9999));

      val channelWriter = new ChannelWriter()
      val channelReader = new ChannelReader()

      val wThread = new Thread(channelWriter)
      val rThread = new Thread(channelReader)

      wThread.start()
      rThread.start()

      Future(while(run) {
        logger.debug("Waiting for a connection on " + port + " with client host " + host + "...")
        val channel:SocketChannel = serverSocketChannel.accept();
        logger.debug(" accepted:" + channel)
        loginUser(channel)
      })

      def loginUser(channel:SocketChannel):Future[Unit] = {
        logger.debug("Added new socket connection: " + channel.getRemoteAddress)
        val audioLoginMaybe = AudioLogin.fromChannel(channel)
        logger.debug("Login?: " + audioLoginMaybe)

        DataService.loginAudioUser(audioLoginMaybe, None, Some(channel)) match {
          case Success(audioLogin) =>
            val ack = AudioAck("Log In Complete")

            logger.debug("Sending ACK")
            AudioAck.toChannel(ack, channel)
            handleSocket(audioLogin.userId, channel)
          case Failure(e) => {
            //TODO: handleSocketError();
            e.printStackTrace
            Future(Unit)
          }
        }
      }

      /**
       * I think i need to repace the future with a true thread
       */
      def handleSocket(userId:Long, channel:SocketChannel):Future[Unit] = Future {
        logger.debug(s"Login Success For: ${userId}/${channel.getRemoteAddress}")
        channel.setOption[java.lang.Boolean](StandardSocketOptions.TCP_NODELAY, true)
        var view:AudioView = DataService.getAudioViewForUser(userId)
        val aUser:Option[AudioUser] = view.people.find(_.userId == userId)

        aUser.foreach { aUser =>
          channelWriter.addUser(aUser)
          channelReader.addUser(aUser)
        }
      }

    }

  }


  def relay(file:Option[String]) = {
    val prop = new java.util.Properties()
    val r:Option[RelayServerChanMult.Relay] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      port <- Option(prop.getProperty("listen"))
      host <- Option(prop.getProperty("hostname"))
    } yield {
      val portAsInt = port.toInt
      new RelayServerChanMult.Relay(portAsInt, host)
    }

    r.getOrElse(new RelayServerChanMult.Relay(55555, "localhost"))
  }

  def main (args:Array[String]):Unit = {
    val file = args.length match {
      case 1 => None
      case 2 => Some(args(1))
    }
    logger.debug(args(0) + " " + file)
    args(0) match {
      case i if i == "r" => relay (file)
    }
  }
}
