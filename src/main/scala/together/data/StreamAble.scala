package together.data

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

import together.util._
import together.audio.AudioServer._
import together.audio.AudioServer.RelayServer

import scala.collection.mutable
import scala.util._

import org.slf4j.LoggerFactory

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

trait StreamAble[T] {
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
  def fromChannel(channel:SocketChannel):Option[T] = ???

  protected def readChannel(lengthInBytes:Int, channel:SocketChannel):ByteBuffer = {
    val buf = ByteBuffer.allocate(lengthInBytes);
    var clCtr = 0

    while(clCtr < lengthInBytes) {
      val bytesRead = channel.read(buf);
      clCtr = clCtr + bytesRead
    }

    buf
  }

  protected def _fromChannel(channel:SocketChannel):Option[JValue] = {
    //val contentLengthBuf = readChannel(contentLengthSize, channel)

    //val contentLengthInChars = contentLengthBuf.getInt()
    //logger.debug(s"RCV: ${contentLength}=${contentLengthInChars}")
    //val contentBuf = readChannel(contentLengthInChars, channel)

    //val content = new String(contentBuf.toString)

    //logger.debug(s"RCV: |${content}|")
    //Option(parse(content))
    def getContentLength = readChannel(contentLengthSize, channel)
    def getContent(contentLength:Int):String = {
      val contentBuf = readChannel(contentLength, channel)
      contentBuf.toString
    }

    fromAny(getContentLength, getContent)

  }

  protected def fromAny(getContentLength: => ByteBuffer, getContent:(Int) => String):Option[JValue] = {
    val contentLengthBuf = getContentLength
    val contentLengthInChars = contentLengthBuf.getInt()
    logger.debug(s"RCV: contentLength=${contentLengthInChars}")
    val content = getContent(contentLengthInChars)
    logger.debug(s"RCV: |${content}|")
    Option(parse(content))
  }

  protected def _fromStream(inputStream:java.io.InputStream):Option[JValue] = {
    val contentLengthInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthSize)
    inputStream.read(contentLengthInBytes)

    val wrapped:ByteBuffer = ByteBuffer.wrap(contentLengthInBytes)
    val contentLength:Int = wrapped.getInt();

    val contentLengthInChars = contentLength
    logger.debug(s"RCV: ${contentLength}=${contentLengthInChars}")
    val contentInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthInChars)
    inputStream.read(contentInBytes)

    val content = new String(contentInBytes.map(_.toChar))

    logger.debug(s"RCV: |${content}|")

    Option(parse(content))

    //Try(json.extract[T]) match {
      //case Success(v) => Option(v)
      //case Failure(e) =>
        //e.printStackTrace()
        //None
    //}
  }

  /**
   * Must always be a factor of 64 (size of packet content).  If Less than fctr of 64,
   * Then pad
   */
  def toStream(src:T, out:java.io.OutputStream):Unit = ???

  /**
   * Channel version of toStream
   */
  def toChannel(src:T, channel:SocketChannel):Unit = ???

  protected def writeChannel(buf:ByteBuffer, channel:SocketChannel):Unit = {
    while(buf.hasRemaining()) {
      channel.write(buf);
    }
  }

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

  protected def _toChannel(json:JValue, channel:SocketChannel):Unit = {
    val (contentLength, content) = dataToStreamable(json)

    println(s"SND: ${contentLength}, '${content}(${content.toString.getBytes.size})'")

    writeChannel(ByteBuffer.allocate(4).putInt(contentLength), channel)
    writeChannel(ByteBuffer.wrap(content.toString.getBytes), channel)
  }

  protected def _toStream(json:JValue, out:java.io.OutputStream):Unit = {
    val (contentLength, content) = dataToStreamable(json)

    println(s"SND: ${contentLength}, '${content}(${content.toString.getBytes.size})'")

    out.write(ByteBuffer.allocate(4).putInt(contentLength).array())
    out.write(content.toString.getBytes)
    out.flush()
  }

  def padRight(src:StringBuilder, padNum:Int):Unit = {
    for(i <- 0 until padNum) {
      src.append(' ');
    }
  }

}
