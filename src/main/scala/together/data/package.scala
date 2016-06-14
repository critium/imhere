package together

import util._
import audio._

import scala.util._
import scala.collection.mutable

import java.net.Socket
import java.nio.channels.ByteChannel

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

package object data {
  var lobbyRoomId = 0l;
  val roomAId = 1l;

  type UserMap = mutable.Map[Long, User]
  type RoomMap = mutable.Map[Long, Room]
  type LongMap = mutable.Map[Long, Long]
  type UserRoomMap = mutable.Map[Long, UserRoom]
  type ViewMap = mutable.Map[AudioViewKey, AudioView]

  case class Domain(id:Long)

  case class Group(id:Long)

  case class User(id:Long, name:String, domainId:Long, groupId:Long, hash:String)

  object User {
    type UserConverter = User => JValue
    implicit def userToJValue:UserConverter = { s => {
      ("id"-> s.id) ~
      ("name" -> s.name) ~
      ("domainId" -> s.domainId) ~
      ("groupId" -> s.groupId)
      ("hash" -> s.hash)
    }}
  }

  case class Room(id:Long, name:String)
  object Room {
    type RoomConverter = Room => JValue
    implicit def webRoomToJValue:RoomConverter = { s => {
      ("id"-> s.id) ~
      ("name" -> s.name)
    }}
  }

  case class UserRoom(roomId:Long, var isTalking:Boolean)

  ////// FOR LOGGING IN (web and audio) //////
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

  ///////// FOR LOGGING IN THE WEB ///////
  case class LoginInfo(hostInfo:HostInfo, user:User) {
    def toAudioLogin = {
      AudioLogin(user.id, user.hash)
    }
  }
  object LoginInfo {
    type LoginInfoConverter = LoginInfo => JValue
    implicit def loginInfoToJValue:LoginInfoConverter = { s => {
      ("hostInfo" -> s.hostInfo) ~
      ("user" -> s.user)
    }}
  }

  //////////// FOR LOGGING IN THE CHANNEL /////////
  case class AudioUser(userId:Long, channel:ByteChannel)
  case class AudioPipeline(id:Long, channel:ByteChannel, buffer:CircularByteBuffer)

  /////////// FOR SETTING VOLUME //////////
  object Volume extends Enumeration {
    val DOWN, UP = Value
  }

  case class AudioViewKey(fromId:Long, toId:Long)
  case class AudioView(userId:Long, var volume:Int){
    def streamFctr = volume / NORMAL_LEVEL
    def changeVolume(vol:Volume.Value) = {
      (this.volume, vol) match {
        case (i, Volume.UP) if (i + 1) >= MAX_VOL =>
          this.volume = MAX_VOL
        case (i, Volume.DOWN) if (i - 1) <= MIN_VOL =>
          this.volume = MIN_VOL
        case (i, Volume.UP) => {
          this.volume = this.volume + 1
        }
        case (i, Volume.DOWN) if i<= MIN_VOL =>
          this.volume = this.volume - 1
      }
      this.volume
    }
  }

  ////////// STREAMABLES ////////////

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

    override def fromChannel(channel:ByteChannel):Option[AudioAck] = {
      _fromChannel(channel) map ( _.extract[AudioAck] )
    }

    override def toChannel(src:AudioAck, channel:ByteChannel):Unit = {
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

    override def fromChannel(channel:ByteChannel):Option[AudioLogin] = {
      _fromChannel(channel) map ( _.extract[AudioLogin] )
    }

    override def toChannel(src:AudioLogin, channel:ByteChannel):Unit = {
      val json:JValue = src
      _toChannel(json, channel)
    }

  }

  case class AudioServerInfo(ip:String, host:String, port:Int)
}
