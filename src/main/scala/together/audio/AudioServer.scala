package together.audio

import together.data._
import together.audio.Conversions._

import java.io._
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.util._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import com.sun.media.sound._

import org.slf4j.LoggerFactory

object AudioServer {
  private val logger = LoggerFactory.getLogger(getClass)

  var encoding = AudioFormat.Encoding.PCM_SIGNED;
  //val rate = 44000f
  val rate = 18000f
  val sampleSize = 16
  val bigEndian = true
  val channels = 1


  // for bandpass
  val voiceLow = 300f
  val voiceHigh = 3000f
  val voiceResonance = (voiceLow + voiceHigh) / 2
  val voiceFrequency = voiceHigh - voiceLow


  def getAudioFormat:AudioFormat = {
    logger.debug("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }

  object RelayServer {
    var port = 0
    var host = ""
    var ip = ""

    @volatile var run = true

    class Relay(incPort:Int, clientHost:String) {
      port = incPort
      host = clientHost

      val serverSocket:ServerSocket = new ServerSocket(port)

      Future(while(run) {
        logger.debug("Waiting for a connection on " + port + "...")
        val socket:Socket = serverSocket.accept();

        logger.debug(" accepted:" + socket)

        loginUser(socket)
      })

      def loginUser(socket:Socket):Future[Unit] = {
        logger.debug("Added new socket connection: " + socket.getInetAddress.getHostAddress)
        val audioLoginMaybe = AudioLogin.fromStream(socket.getInputStream())

        DataService.loginAudioUser(audioLoginMaybe, socket) match {
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

        //val bufSize = 512*5
        val bufSize = 64*1
        var bytesRead = 0

        // Pipe output to all connections
        val out = socket.getOutputStream()

        while(true) {
          val view= DataService.getAudioViewForUser(userId)
          if(view.people.size > 1) {
            val others:List[AudioUser] = view.people.filter(_.userId != userId)

            val level = others.size
            val baseline = Array.ofDim[Byte](bufSize)
            var sumStreams:Array[Byte] = others.foldLeft(baseline){ ( l,c ) =>
              val readBytes = Array.ofDim[Byte](bufSize)
              val byteCt= c.socket.getInputStream().read(readBytes)
              if(byteCt == -1) {
                l
              } else {
                for(i <- 0 until bufSize) {
                  l(i) = (l(i) + (((readBytes(i) / level)) * (c.streamFctr)).toInt).toByte
                }
                l
              }
            }
            print('.')
            out.write(sumStreams)
            out.flush()

          } else {
            logger.debug("no connections. sleeping")
            Thread.sleep(1*1000)
          }
        }
      }
    }

    def applyFctr(ad: Array[Byte], fctr:Float):Unit = {
      var i = 0
      while (i<ad.length) {
        ad(i) = (ad(i) * fctr).toByte
      }
    }

    def getLevel(ad: Array[Byte]):Double = {
      var sumPos = 0d
      var sumNeg = 0d
      var i = 0
      while (i<ad.length) {
        if(ad(i) > 0) {
          sumPos += ad(i)
        } else {
          sumNeg += ad(i)
        }
        i += 1
      }
      sumPos - sumNeg
    }
  }


  def relay(file:Option[String]) = {
    val prop = new java.util.Properties()
    val r:Option[RelayServer.Relay] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      port <- Option(prop.getProperty("listen"))
      host <- Option(prop.getProperty("hostname"))
    } yield {
      val portAsInt = port.toInt
      new RelayServer.Relay(portAsInt, host)
    }

    r.getOrElse(new RelayServer.Relay(55555, "localhost"))
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
