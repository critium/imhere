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

object AudioServerChannelMult {
  private val logger = LoggerFactory.getLogger(getClass)
  val ds = DataService.default


  object RelayServerChanMult {
    var port = 0
    var host = ""
    var ip = ""

    @volatile var run = true

    class Relay(incPort:Int, clientHost:String) {
      port = incPort
      host = clientHost

      ds.registerServer(AudioServerInfo(ip, host, port))

      val serverSocketChannel:ServerSocketChannel = ServerSocketChannel.open();

      serverSocketChannel.bind(new InetSocketAddress(port))

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

        val res:Option[Future[Unit]] = audioLoginMaybe map { audioLogin =>
          ds.loginAudio(audioLogin, channel) match {
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

        res match {
          case Some(v) => v
          case _ => Future(Unit)
        }
      }

      def handleSocket(userId:Long, channel:SocketChannel):Future[Unit] = Future {
        logger.debug(s"Login Success For: ${userId}/${channel.getRemoteAddress}")
        channel.setOption[java.lang.Boolean](StandardSocketOptions.TCP_NODELAY, true)
        val aUser:Option[AudioPipeline] = ChannelService.getAudioPipeline(userId)

        aUser.foreach { aUser =>
          ChannelService.addUser(aUser, lobbyRoomId)
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
