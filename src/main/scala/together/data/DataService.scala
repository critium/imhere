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

/**
 * Temporary dataservice for volatile objects.  Should be replaced with cache such as redis/hazelcast/...
 */
object DataService {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates the empty lobby room
   */
  private val lobby = Room(lobbyRoomId, "Lobby", mutable.Map[Long, User]())
  private val roomA = Room(roomAId, "Room A", mutable.Map[Long, User]())

  // all rooms
  @volatile private var _rooms = mutable.Map[Long, Room](lobbyRoomId -> lobby, roomAId -> roomA)

  // all users
  @volatile private var _users = mutable.Map[Long, User]()

  // all sockets
  @volatile private var _sockets = mutable.Map[Long, Socket]()

  // all channels
  @volatile private var _channels = mutable.Map[Long, SocketChannel]()

  // views are rooms and users on the user's perspective
  @volatile private var _views = mutable.Map[Long, View]()

  // all buffers.  this belongs on the audioserver
  @volatile private var _buffers = mutable.Map[Long, CircularByteBuffer]()

  // all servers, prepare for future where this is many
  private var _server:Option[AudioServerInfo] = None

  //////////////// testing implementations only ///////////
  // MISSING SECURITY

  def hash(userId:Long) = "#"

  def getUsers(userId:Long):mutable.Map[Long, User] = _users

  def getUser(userId:Long):Option[User] = _users.get(userId)

  def getViews(userId:Long):mutable.Map[Long, View] = _views

  def getView(userId:Long):Option[View] = _views.get(userId)

  def getRoom(user:User, roomId:Long):Option[Room] = getRooms(user).get(roomId)

  def getRooms(user:User):mutable.Map[Long, Room] = _rooms

  def getServer(user:User):Option[AudioServerInfo] = _server

  def getBuffer(userId:Long):CircularByteBuffer = _buffers(userId)

  def getHostInfo(user:User):HostInfo = {
    var server = getServer(user).getOrElse(AudioServerInfo("", "", 0))
    HostInfo(1, server.ip, server.host, server.port)
  }

  def getRoomIdForUserId(userId:Long):Long = {
    getView(userId).get.web.currentRoom.id // hack
  }

  def getAudioBufForUser(userId:Long):CircularByteBuffer = {
    getBuffer(userId)
  }

  def registerServer(audioServer:AudioServerInfo):Unit = {
    _server = Some(audioServer)
  }





  ///////////////// WORKs BUT THIS IS REALLY A DUMMY IMPL ///////////////
  // NEED TO REMOVE access to the underscore vals before it can graduate
  def loginAudioUser(audioLogin:Option[AudioLogin], socket:Option[Socket], channel:Option[SocketChannel]):Try[AudioLogin] = {
    audioLogin match {
      case Some(audioLogin) =>
        // TODO: do something with the hash
        // TODO: Can be mis-matched if user changes rooms before audio becomes available

        // get the ppl in the lobby
        val user = getUser(audioLogin.userId).get //hack!
        val pplInLobby = getPeopleInRoomId(user, lobbyRoomId)

        (socket, channel) match {
          case (Some(socket), _) => _sockets += (audioLogin.userId -> socket)
          case (_, Some(channel)) => _channels += (audioLogin.userId -> channel)
          case _ => Unit
        }

        _buffers += (audioLogin.userId -> CircularByteBuffer.newBuf(audioLogin.userId.toInt))

        Success(audioLogin)
      case _ => Failure(new IllegalArgumentException("audioLogin is None"))
    }
  }

  def getAudioViewForUser(userId:Long):AudioView = {
    val roomId = getRoomIdForUserId(userId)
    val user = getUser(userId).get // hack!
    val theUsers = getPeopleInRoomId(user, roomId)
    val people:List[AudioUser] = theUsers.flatMap { case (k, u) =>
      for {
        buf <- _buffers.get(u.id)
      } yield {
        val s = _sockets.get(u.id)
        val c = _channels.get(u.id)
        AudioUser(k, s, c, buf, 5, u.hash)
      }
    }.toList

    //HACK!
    AudioView(userId, getRoom(user, roomId).get, people)
  }







  ///////////////// WORK BEGINS ///////////////

  /**
   * Puts a person into the user group and the default room (no room?)
   */
  def login(user:User):Option[LoginInfo] = {
    //TODO: do some security thing here

    // Add the user to the list.
    getUsers(user.id) += (user.id -> user)

    // add the person to the lobby
    addPersonToRoom(lobbyRoomId, user)

    // Create the Login Info.  Normally this should be based on Domain Info.  Basically we need to put the people
    // in the same domain on the same server.  For larger instances this should break down into groups as well.
    val webView = WebView(user.id, lobby.toWebRoom, user.hash)

    val view = View(webView, None)

    // add the view
    getViews(user.id) += (user.id -> view)

    // get the ip and host
    val hostInfo = getHostInfo(user)

    // return the login info
    Some(LoginInfo(hostInfo, webView))
  }


  def updateView(userId:Long, view:View):Boolean = {
    Try(getViews(userId) += (userId -> view)) match {
      case Success(_) => true
      case Failure(e) =>
        e.printStackTrace
        false
    }
  }

  def moveToRoom(userId:Long, roomId:Long):Boolean = {
    logger.debug(s"moving $userId to $roomId")

    // validate(roomId, userId)
    val userOpt = getUser(userId)

    userOpt.map { user =>
      movePersonToRoom(roomId, user)
    } match {
      case Some(v) => v
      case _ => false
    }
  }

  private def movePersonToRoom(roomId:Long, user:User):Boolean = {
    removePersonFromRoom(user) && addPersonToRoom(roomId, user) && changeView(roomId, user)
  }

  private def changeView(roomId:Long, user:User):Boolean = {
    val res = for {
      view <- getView(user.id)
      room <- getRoom(user, roomId)
    } yield {
      val webRoom = room.toWebRoom
      val webC = view.web.copy(currentRoom = webRoom)
      val audioC = view.audio.map(_.copy(currentRoom = room))
      val newView = View(webC, audioC)

      updateView(user.id, newView)
    }

    res match {
      case Some(v) => v
      case _ => false
    }
  }

  private def removePersonFromRoom(user:User):Boolean = {
    val rooms = getRooms(user)

    rooms.values.map{  r =>
      val pSize1 = r.people.size
      r.people.remove(user.id)
      val pSize2 = r.people.size

      if(pSize2 != pSize2) {
        println(s"Removed User from ${r.id}")
      }
    }

    true
  }

  private def addPersonToRoom(roomId:Long, user:User):Boolean = {
    val roomOpt = getRoom(user, roomId)

    roomOpt match {
      case Some(room) =>
        if(!room.people.contains(user.id)) {
          room.people += (user.id -> user)
        }
        true
      case _ => false
    }
  }

  def getPeopleInRoomId(user:User, roomId:Long):Map[Long, User] = {
    val roomOpt = getRoom(user, roomId)

    roomOpt match {
      case Some(room) => room.people.toMap
      case _ => Map[Long,User]()
    }
  }


  def listRooms(userId:Long):List[WebRoom] = {
    getUser(userId) match {
      case Some(user) => getRooms(user).values.map(_.toWebRoom).toList
      case _ => List[WebRoom]()
    }
  }


}
