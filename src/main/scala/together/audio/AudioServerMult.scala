package together.audio

import together.data._
import together.audio.Conversions._

import java.io._
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.util._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import com.sun.media.sound._

import org.slf4j.LoggerFactory

/**
 * This is the 2nd version. Its coded to use the disruptor and has a single read thread and a separate write thread
 * It is hard coded to use sockets
 */
object AudioServerMult {
  private val logger = LoggerFactory.getLogger(getClass)

  logger.info("STARTING THE MULT SERVER")

  // TODO: Check perf differnce with using native byte buffers
  class SocketWriter() extends Runnable {
    var users:mutable.MutableList[AudioUser] = mutable.MutableList[AudioUser]()

    val baseline = Array.ofDim[Byte](bufferLengthInBytes)
    val readBytes = Array.ofDim[Byte](bufferLengthInBytes)

    def addUser(auser:AudioUser):Unit = {
      users += auser
    }

    def writeMultiple(aUser:AudioUser) = {
      val byteCt = aUser.socket.get.getInputStream().read(readBytes)
      val buf = if(byteCt == -1) {
        Thread.sleep(1*1000)
        Thread.`yield`
        baseline
      } else {
        readBytes
      }

      aUser.buf.write(buf)
    }

    def run() = {
      while(true) {
        users.foreach( writeMultiple(_) )
      }
    }
  }

  class SocketReader() extends Runnable {
    case class SocketReaderContext(userId:Long, var view:AudioView, var others:List[AudioUser], var level:Int, var readCt:Int, out:OutputStream)

    var contexts:mutable.MutableList[SocketReaderContext] = mutable.MutableList[SocketReaderContext]()

    val baseline = Array.ofDim[Byte](bufferLengthInBytes)
    val readBytes = Array.ofDim[Byte](bufferLengthInBytes)

    def addUser(aUser:AudioUser):Unit = {
      val userId = aUser.userId
      var view:AudioView = DataService.getAudioViewForUser(userId)

      var readCt:Int = 0
      var others:List[AudioUser] = view.people.filter(_.userId != userId)
      others.map { _.buf.register(view.userId)} // register here
      var level = others.size
      val out = aUser.socket.get.getOutputStream()
      contexts += SocketReaderContext(userId, view, others, level, readCt, out)
    }

    def readMultiple(context:SocketReaderContext):Unit = {
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
        context.out.write(sumStreams)
        context.out.flush()
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

  object RelayServerMult {
    var port = 0
    var host = ""
    var ip = ""


    @volatile var run = true

    class Relay(incPort:Int, clientHost:String) {
      port = incPort
      host = clientHost

      DataService.registerServer(AudioServerInfo(ip, host, port))

      val serverSocket:ServerSocket = new ServerSocket(port)

      val socketWriter = new SocketWriter()
      val socketReader = new SocketReader()

      val wThread = new Thread(socketWriter)
      val rThread = new Thread(socketReader)

      wThread.start()
      rThread.start()

      Future(while(run) {
        logger.debug("Waiting for a connection on " + port + " with client host " + host + "...")
        val socket:Socket = serverSocket.accept();
        logger.debug(" accepted:" + socket)
        loginUser(socket)
      })

      def loginUser(socket:Socket):Future[Unit] = {
        logger.debug("Added new socket connection: " + socket.getInetAddress.getHostAddress)
        val audioLoginMaybe = AudioLogin.fromStream(socket.getInputStream())

        DataService.loginAudioUser(audioLoginMaybe, Some(socket), None) match {
          case Success(audioLogin) =>
            val ack = AudioAck("Log In Complete")

            logger.debug("Sending ACK")
            AudioAck.toStream(ack, socket.getOutputStream)
            handleSocket(audioLogin.userId, socket)
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
      def handleSocket(userId:Long, socket:Socket):Future[Unit] = Future {
        logger.debug(s"Login Success For: ${userId}/${socket.getInetAddress.getHostAddress}")
        socket.setTcpNoDelay(true)
        var view:AudioView = DataService.getAudioViewForUser(userId)
        val aUser:Option[AudioUser] = view.people.find(_.userId == userId)

        aUser.foreach { aUser =>
          socketWriter.addUser(aUser)
          socketReader.addUser(aUser)
        }
      }

    }

  }


  def relay(file:Option[String]) = {
    val prop = new java.util.Properties()
    val r:Option[RelayServerMult.Relay] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      port <- Option(prop.getProperty("listen"))
      host <- Option(prop.getProperty("hostname"))
    } yield {
      val portAsInt = port.toInt
      new RelayServerMult.Relay(portAsInt, host)
    }

    r.getOrElse(new RelayServerMult.Relay(55555, "localhost"))
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
