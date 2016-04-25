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

object AudioServer {
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
    println("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }

  object RelayServer {
    var port = 0
    var host = ""
    var ip = ""

    @volatile var run = true

    class Relay(incPort:Int) {
      port = incPort

      val serverSocket:ServerSocket = new ServerSocket(port)

      Future(while(run) {
        print("Waiting for a connection on " + port + "...")
        val socket:Socket = serverSocket.accept();

        println(" accepted:" + socket)

        loginUser(socket)
      })

      def loginUser(socket:Socket):Future[Unit] = {
        println("Added new socket connection: " + socket.getInetAddress.getHostAddress)
        val audioUserMaybe = AudioUser.fromStream(socket.getInputStream())

        DataService.loginAudioUser(audioUserMaybe) match {
          case Success(audioView) =>
            handleSocket(audioView)
          case Failure(e) => {
            e.printStackTrace
            Future(Unit)
          }
        }
      }

      // /**
      //  * I think i need to repace the future with a true thread
      //  */
       def handleSocket(audioView:AudioView):Future[Unit] = Future {
         Unit
       }
      //   println("Added new socket connection: " + socket.getInetAddress.getHostAddress)
      //   val newConn =  Conns(getConnId, socket, socket.getInetAddress.getHostAddress)
      //   allConns = newConn +: allConns

      //   //val bufSize = 512*5
      //   val bufSize = 64*1
      //   var bytesRead = 0


      //   // Pipe output to all connections
      //   val out = newConn.socket.getOutputStream()
      //   while(true) {
      //     if(allConns.size > 1) {
      //       val others:List[Conns] = allConns.filter(_.connId != newConn.connId)


      //       val level = others.size
      //       val baseline = Array.ofDim[Byte](bufSize)
      //       var sumStreams:Array[Byte] = others.foldLeft(baseline){ ( l,c ) =>
      //         val readBytes = Array.ofDim[Byte](bufSize)
      //         val byteCt= c.socket.getInputStream().read(readBytes)
      //         if(byteCt == -1) {
      //           l
      //         } else {
      //           for(i <- 0 until bufSize) {
      //             l(i) = (l(i) + (((readBytes(i) / level)) * c.streamFctr).toInt).toByte
      //           }
      //           l
      //         }
      //       }
      //       print('.')
      //       out.write(sumStreams)
      //       out.flush()

      //     } else {
      //       println("no connections. sleeping")
      //       Thread.sleep(1*1000)
      //     }
      //   }
      // }
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
    val port:Option[Int] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      port <- Option(prop.getProperty("listen"))
    } yield {
      port.toInt
    }
    val r = new RelayServer.Relay(port.getOrElse(55555))
    readLine()
  }

  def main (args:Array[String]):Unit = {
    val file = args.length match {
      case 1 => None
      case 2 => Some(args(1))
    }
    println(args(0) + " " + file)
    args(0) match {
      case i if i == "r" => relay (file)
    }
  }
}
