package together

import scala.util._
import scala.collection.mutable

import java.net.Socket;
import java.nio.ByteBuffer

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

package object data {
  var lobbyRoomId = 0l;
  val normalLevel = 5

  case class Domain(id:Long)

  case class Group(id:Long)

  case class User(id:Long, name:String, domainId:Long, groupId:Long, hash:String)
  object User {
    type UserConverter = User => JValue
    implicit def userToJValue:UserConverter = { s => {
      ("id"-> s.id) ~
      ("name" -> s.name) ~
      ("domainId" -> s.domainId) ~
      ("groupId" -> s.groupId) ~
      ("hash" -> s.hash)
    }}
  }


  case class Room(id:Long, name:String, people:mutable.Map[Long, User]) {
    def toWebRoom = {
      WebRoom(id, name, people.values.toList)
    }
  }


  case class WebRoom(id:Long, name:String, people:List[User])
  object WebRoom {
    type WebRoomConverter = WebRoom => JValue
    implicit def webRoomToJValue:WebRoomConverter = { s => {
      ("id"-> s.id) ~
      ("name" -> s.name) ~
      ("people" -> s.people)
    }}
  }

  case class WebView(userId:Long, currentRoom:WebRoom, hash:String)
  object WebView {
    type WebViewConverter = WebView => JValue
    implicit def webViewToJValue:WebViewConverter = { s => {
      ("userId"-> s.userId) ~
      ("currentRoom" -> s.currentRoom ) ~
      ("hash" -> s.hash)
    }}
  }

  case class HostInfo(id:Long, ip:String, name:String, port:Int)
  object HostInfo {
    type HostInfoConverter = HostInfo => JValue
    implicit def hostInfoToJValue:HostInfoConverter = { s => {
      ("id"-> s.id) ~
      ("ip" -> s.ip) ~
      ("port" -> s.port) ~
      ("name" -> s.name)
    }}
  }

  case class LoginInfo(hostInfo:HostInfo, view:WebView)
  object LoginInfo {
    type LoginInfoConverter = LoginInfo => JValue
    implicit def loginInfoToJValue:LoginInfoConverter = { s => {
      ("hostInfo" -> s.hostInfo) ~
      ("view" -> s.view)
    }}
  }




  case class AudioView(userId:Long, currentRoom:Room, people:List[AudioUser])

  case class AudioUser(userId:Long, socket:Socket, volume:Int, hash:String){
    def streamFctr = normalLevel * volume
  }

  case class View(web:WebView, audio:Option[AudioView])



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

      val json:JValue = parse(content)

      Try(json.extract[AudioLogin]) match {
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
