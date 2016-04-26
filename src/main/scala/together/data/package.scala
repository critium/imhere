package together

import scala.util._
import scala.collection.mutable

import java.net.Socket;
import java.nio.ByteBuffer

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

package object data {
  var lobbyRoomId = 0l;
  val normalLevel = 5

  case class Room(id:Long, name:String, people:mutable.Map[Long, User])

  case class Domain(id:Long)

  case class Group(id:Long)

  case class User(id:Long, name:String, domain:Domain, group:Group, hash:String)



  case class LoginInfo(hostInfo:HostInfo, view:WebView)

  case class WebView(userId:Long, currentRoom:Room, hash:String, people:Map[Long, User])



  case class AudioView(userId:Long, currentRoom:Room, people:List[AudioUser])

  case class AudioUser(userId:Long, socket:Socket, volume:Int, hash:String){
    def streamFctr = normalLevel * volume
  }

  case class View(web:WebView, audio:Option[AudioView])

  case class HostInfo(id:Long, ip:String, name:String, port:Int)


  case class AudioLogin(userId:Long, hash:String)

  object AudioLogin {
    implicit val formats = DefaultFormats

    val packetSize = 64
    val contentLengthSize = java.lang.Integer.SIZE / java.lang.Byte.SIZE

    type AudioLoginConverter = AudioLogin => JValue
    implicit def audioLoginToJValue:AudioLoginConverter= { a =>{
      ("userId" -> a.userId ) ~
      ("hash"   -> a.hash)
    }}

    def fromStream(inputStream:java.io.InputStream):Option[AudioLogin] = {
      val contentLengthInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthSize)
      inputStream.read(contentLengthInBytes)

      val wrapped:ByteBuffer = ByteBuffer.wrap(contentLengthInBytes)
      val contentLength:Int = wrapped.getInt();

      val contentLengthInChars = contentLength
      println(s"RCV: ${contentLength}=${contentLengthInChars}")
      val contentInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthInChars)
      inputStream.read(contentInBytes)

      val content = new String(contentInBytes.map(_.toChar))

      println(s"RCV: |${content}|")

      Try(parse(content).extract[AudioLogin]) match {
        case Success(v) => Option(v)
        case Failure(e) =>
          e.printStackTrace()
          None
      }
    }

    /**
     * Must always be a factor of 64 (size of packet content).  If Less than fctr of 64,
     * Then pad
     */
    def toStream(audioLogin:AudioLogin, out:java.io.OutputStream):Unit = {
      val audioLoginJson:JValue = audioLogin
      val contentSrc:StringBuilder = new StringBuilder(write(audioLogin))
      val contentLengthRaw:Int = contentSrc.length
      val contentLengthSizeRem:Int = packetSize - (contentLengthRaw % packetSize)

      // add the padding
      val contentLength = contentLengthRaw + contentLengthSizeRem
      padRight(contentSrc, contentLengthSizeRem)
      val content = contentSrc.toString

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
}
