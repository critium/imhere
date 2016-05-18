package together

import util._

import scala.util._
import scala.collection.mutable

import java.net.Socket
import java.nio.channels.SocketChannel

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

package object data {
  var lobbyRoomId = 0l;
  val roomAId = 1l;
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

  case class LoginInfo(hostInfo:HostInfo, view:WebView) {
    def toAudioLogin = {
      AudioLogin(view.userId, view.hash)
    }
  }
  object LoginInfo {
    type LoginInfoConverter = LoginInfo => JValue
    implicit def loginInfoToJValue:LoginInfoConverter = { s => {
      ("hostInfo" -> s.hostInfo) ~
      ("view" -> s.view)
    }}
  }




  case class AudioView(userId:Long, currentRoom:Room, people:List[AudioUser])

  case class AudioUser(userId:Long, socket:Option[Socket], channel:Option[SocketChannel], buf:CircularByteBuffer, volume:Int, hash:String){
    def streamFctr = volume / normalLevel
  }

  case class View(web:WebView, audio:Option[AudioView])



  case class AudioAck(msg:String)
  object AudioAck extends StreamAble[AudioAck] {
    type AudioAckConverter = AudioAck => JValue
    implicit def audioAckToJValue:AudioAckConverter= { a =>{
      ("msg"   -> a.msg)
    }}

    override def fromStream(inputStream:java.io.InputStream):Option[AudioAck] = {
      _fromStream(inputStream) map ( _.extract[AudioAck] )
    }


    override def toStream(src:AudioAck, out:java.io.OutputStream):Unit = {
      val json:JValue = src
      _toStream(json, out)
    }

    override def fromChannel(channel:SocketChannel):Option[AudioAck] = {
      _fromChannel(channel) map ( _.extract[AudioAck] )
    }

    override def toChannel(src:AudioAck, channel:SocketChannel):Unit = {
      val json:JValue = src
      _toChannel(json, channel)
    }
  }

  case class AudioLogin(userId:Long, hash:String)

  object AudioLogin extends StreamAble[AudioLogin] {
    type AudioLoginConverter = AudioLogin => JValue
    implicit def audioLoginToJValue:AudioLoginConverter= { a =>{
      ("userId" -> a.userId ) ~
      ("hash"   -> a.hash)
    }}

    override def fromStream(inputStream:java.io.InputStream):Option[AudioLogin] = {
      _fromStream(inputStream) map (_.extract[AudioLogin])
    }


    override def toStream(src:AudioLogin, out:java.io.OutputStream):Unit = {
      val json:JValue = src
      _toStream(json, out)
    }

    override def fromChannel(channel:SocketChannel):Option[AudioLogin] = {
      _fromChannel(channel) map ( _.extract[AudioLogin] )
    }

    override def toChannel(src:AudioLogin, channel:SocketChannel):Unit = {
      val json:JValue = src
      _toChannel(json, channel)
    }

  }

  case class AudioServerInfo(ip:String, host:String, port:Int)
}
