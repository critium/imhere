package together.data

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import together.util._
import together.audio.Conversions._
import together.audio.AudioServer._
import together.audio.AudioServer.RelayServer

import scala.collection.mutable
import scala.util._

import org.slf4j.LoggerFactory

trait ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def readChannel(lengthInBytes:Int, channel:SocketChannel, toBuf:Option[ByteBuffer] = None):ByteBuffer = {
    //logger.debug(s"RCV: readchannel=${lengthInBytes}")
    val buf = toBuf match {
      case Some(b) => b
      case _ => ByteBuffer.allocate(lengthInBytes);
    }
    var clCtr = 0

    while(clCtr < lengthInBytes) {
      val bytesRead = channel.read(buf);
      clCtr = clCtr + bytesRead
    }

    //logger.debug(s"RCV: readchannel=complete ${buf.array().map(printBinary(_)).mkString(",")}")
    buf
  }

  protected def writeChannel(buf:ByteBuffer, channel:SocketChannel):Unit = {
    while(buf.hasRemaining()) {
      channel.write(buf);
    }
  }

}
