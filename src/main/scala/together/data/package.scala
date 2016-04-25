package together

import scala.collection.mutable

import java.net.Socket;
import java.nio.ByteBuffer

package object data {
  var lobbyRoomId = 0l;
  case class Room(id:Long, name:String, people:mutable.Map[Long, User])

  case class Domain(id:Long)

  case class Group(id:Long)

  case class User(id:Long, name:String, domain:Domain, group:Group, hash:String)



  case class LoginInfo(hostInfo:HostInfo, view:WebView)

  case class WebView(userId:Long, currentRoom:Room, people:Map[Long, User])



  case class AudioView(userId:Long, currentRoom:Room, people:List[AudioUser])

  case class AudioUser(userId:Long, socket:Socket, volume:Int, hash:String)

  case class View(web:WebView, audio:Option[AudioView])


  case class HostInfo(id:Long, ip:String, name:String, port:Int)

  case class AudioLogin(user:User)

  object AudioUser {
    val packetSize = 64
    val contentLengthSize = java.lang.Integer.SIZE
    def fromStream(inputStream:java.io.InputStream):Option[AudioUser] = {
      val contentLengthInBytes:Array[Byte] = Array.ofDim[Byte](contentLengthSize)
      inputStream.read(contentLengthInBytes)

      val wrapped:ByteBuffer = ByteBuffer.wrap(contentLengthInBytes)
      val contentLength:Int = wrapped.getInt();

      val contentInBytes:Array[Byte] = Array.ofDim[Byte](contentLength)
      inputStream.read(contentInBytes)

      val content = new String(contentInBytes)

      println("Content: " + content)
      None
    }

    /**
     * Must always be a factor of 64 (size of packet content).  If Less than fctr of 64,
     * Then pad
     */
    def toStream(contentSrc:StringBuilder, out:java.io.OutputStream):Unit = {
      val contentLengthRaw:Int = contentSrc.length
      val contentLengthSizeRem:Int = contentLengthRaw % packetSize

      // add the padding
      val contentLength = contentLengthRaw + contentLengthSizeRem
      padRight(contentSrc, contentLengthSizeRem)
      val content = contentSrc.toString

      out.write(contentLength.toByte)
      out.write(content.toByte)
    }

    def padRight(src:StringBuilder, padNum:Int):Unit = {
      for(i <- 0 until padNum) {
        src.append(' ');
      }
    }
  }
}
