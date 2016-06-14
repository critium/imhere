package together.data

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

import together.util._
import together.audio._
import together.audio.Conversions._

import scala.collection.mutable
import scala.util._

import org.slf4j.LoggerFactory

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

trait StreamAble[T] extends ChannelSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val formats = DefaultFormats

  val packetSize = 64
  val contentLengthSize = java.lang.Integer.SIZE / java.lang.Byte.SIZE

  /**
   * Reads object from stream
   */
  def fromStream(inputStream:java.io.InputStream):Option[T] = ???

  /**
   * Channel version of fromStream
   */
  def fromChannel(channel:ByteChannel):Option[T] = ???

  protected def _fromChannel(channel:ByteChannel):Option[JValue] = {
    def getContentLength = {
      val buf = readChannel(contentLengthSize, channel)
      buf.getInt(0)
    }
    def getContent(contentLength:Int):String = {
      val contentBuf = readChannel(contentLength, channel)
      new String(contentBuf.array.map(_.toChar))
    }

    fromAny(getContentLength, getContent)

  }

  protected def fromAny(getContentLength: => Int , getContent:(Int) => String):Option[JValue] = {
    val contentLengthInChars= getContentLength
    logger.debug(s"RCV: contentLength=${contentLengthInChars}")

    val content = getContent(contentLengthInChars)
    logger.debug(s"RCV: |${content}|")

    Option(parse(content))

    //Try(json.extract[T]) match {
      //case Success(v) => Option(v)
      //case Failure(e) =>
        //e.printStackTrace()
        //None
    //}
  }

  protected def _fromStream(inputStream:java.io.InputStream):Option[JValue] = {
    def getContentLength = {
      val contentLengthInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthSize)
      inputStream.read(contentLengthInBytes)
      val wrapped:ByteBuffer = ByteBuffer.wrap(contentLengthInBytes)
      wrapped.getInt(0)
    }

    def getContent(contentLength:Int):String = {
      val contentLengthInChars = contentLength
      logger.debug(s"RCV: ${contentLength}=${contentLengthInChars}")
      val contentInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthInChars)
      inputStream.read(contentInBytes)
      new String(contentInBytes.map(_.toChar))
    }

    fromAny(getContentLength, getContent)
  }

  /**
   * Must always be a factor of 64 (size of packet content).  If Less than fctr of 64,
   * Then pad
   */
  def toStream(src:T, out:java.io.OutputStream):Unit = ???

  /**
   * Channel version of toStream
   */
  def toChannel(src:T, channel:ByteChannel):Unit = ???

  protected def dataToStreamable(json:JValue):(Int, String) = {
    val contentSrc:StringBuilder = new StringBuilder(write(json))
    val contentLengthRaw:Int = contentSrc.length
    val contentLengthSizeRem:Int = packetSize - (contentLengthRaw % packetSize)

    // add the padding
    val contentLength = contentLengthRaw + contentLengthSizeRem
    padRight(contentSrc, contentLengthSizeRem)
    val content = contentSrc.toString

    (contentLength, content)
  }

  protected def _toChannel(json:JValue, channel:ByteChannel):Unit = {
    val (contentLength, content) = dataToStreamable(json)

    println(s"SND: ${contentLength}, '${content}(${content.toString.getBytes.size})'")

    val countBytes = ByteBuffer.allocate(contentLengthSize).putInt(contentLength).array
    val contentBytes = content.toString.getBytes
    val messageInBytes:Array[Byte] = Array.ofDim[Byte](countBytes.size + contentBytes.size)

    System.arraycopy(countBytes   , 0 , messageInBytes , 0               , countBytes.size)
    System.arraycopy(contentBytes , 0 , messageInBytes , countBytes.size , contentBytes.size)

    val finalBuf = ByteBuffer.wrap(messageInBytes)
    //val finalBuf = totalBuf.put(countBuf).put(contentBuf, contentLengthSize)
    //writeChannel(ByteBuffer.allocate(contentLengthSize).putInt(contentLength), channel)
    //writeChannel(ByteBuffer.wrap(content.toString.getBytes), channel)

    println(s"SND: ${countBytes.map(printBinary(_)).mkString(",")}")
    //println(s"SND: ${contentBytes.map(printBinary(_)).mkString(",")}")
    //println(s"SND: ${finalBuf.array.map(printBinary(_)).mkString(",")}")
    writeChannel(finalBuf, channel)
  }

  protected def _toStream(json:JValue, out:java.io.OutputStream):Unit = {
    val (contentLength, content) = dataToStreamable(json)

    println(s"SND: ${contentLength}, '${content}(${content.toString.getBytes.size})'")

    out.write(ByteBuffer.allocate(contentLengthSize).putInt(contentLength).array())
    out.write(content.toString.getBytes)
    out.flush()
  }

  def padRight(src:StringBuilder, padNum:Int):Unit = {
    for(i <- 0 until padNum) {
      src.append(' ');
    }
  }

}
