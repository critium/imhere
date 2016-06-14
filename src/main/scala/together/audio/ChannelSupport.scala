package together.audio

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

import together.util._
import together.audio.Conversions._

import scala.collection.mutable
import scala.util._

import org.slf4j.LoggerFactory

trait ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  protected def readChannel(lengthInBytes:Int, channel:ByteChannel, toBuf:Option[ByteBuffer] = None):ByteBuffer = {
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

  protected def writeChannel(buf:ByteBuffer, channel:ByteChannel):Unit = {
    while(buf.hasRemaining()) {
      channel.write(buf);
    }
  }

}
