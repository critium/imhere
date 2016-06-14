package together.data

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

import together.util._
import together.audio._

import scala.collection.mutable.{Map => MutableMap}
import scala.util._

import org.slf4j.LoggerFactory

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

trait DataServiceTrait {
  private val logger = LoggerFactory.getLogger(getClass)
  def loginUser(user:User):Option[LoginInfo]
  def loginAudio(audioLogin:AudioLogin, channel:ByteChannel):Try[AudioLogin]
  def moveToRoom(userId:Long, roomId:Long):Boolean
  def createRoom(userId:Long, name:String):Room
  def closeRoom(userId:Long, roomId:Long):Boolean
  def changeVolume(userId:Long, toUserId:Long, vol:Volume.Value):Option[Int]
  def startTalking(userId:Long, toUserId:Long):Boolean
  def stopTalking(userId:Long):Boolean
  def whosTalking(userId:Long):List[Long]
  def listRooms(userId:Long):List[Room]
  def logout(userId:Long):Boolean
  def registerServer(audioServer:AudioServerInfo):Unit
  def getAudioViewForUser(userId:Long):List[AudioView]
  def getRoomIdForUser(userId:Long):Option[Long]
  def getHostInfo(user:User):HostInfo
}

object DataService {
  val default = new DataServiceImpl()
}

class DataServiceImpl extends DataServiceTrait {
  private val logger = LoggerFactory.getLogger(getClass)
  //private var domains = MutableMap[Long, Domain]()
  //private var groups = MutableMap[Long, Group]()

  /**
   * Creates the empty lobby room
   */
  private val lobby = Room(lobbyRoomId, "Lobby")
  private val roomA = Room(roomAId, "Room A")

  //////////////// PRIVATE VARS //////////////
  // all rooms
  @volatile private var _rooms:RoomMap = MutableMap[Long, Room](lobbyRoomId -> lobby, roomAId -> roomA)

  // all users
  @volatile private var _users:UserMap = MutableMap[Long, User]()

  // all views
  @volatile private var _views:ViewMap = MutableMap[AudioViewKey, AudioView]()

  //relationship between users and rooms
  @volatile private var _userRoom:UserRoomMap = MutableMap[Long, UserRoom]()

  // all servers, prepare for future where this is many
  private var _server:Option[AudioServerInfo] = None


  ////////// UNSAFE FUNCTIONS ////////////
  ////////// UNSAFE FUNCTIONS ////////////
  ////////// UNSAFE FUNCTIONS ////////////

  def getRooms(userId:Long):RoomMap = _rooms

  def getRoom(userId:Long, roomId:Long):Option[Room] = getRooms(userId).get(roomId)

  def getUserRoom(userId:Long):UserRoomMap = _userRoom

  def getUser(userId:Long):Option[User] = _users.get(userId)

  def getUsers(userId:Long):UserMap = _users

  def registerServer(audioServer:AudioServerInfo):Unit = {
    _server = Some(audioServer)
  }

  def getViews(userId:Long) = _views

  def getView(userId:Long, otherUserId:Long):Option[AudioView] = _views.get(AudioViewKey(userId, otherUserId))

  def getServer(user:User):Option[AudioServerInfo] = _server


  ////////// SAFE FUNCTIONS ///////////
  ////////// SAFE FUNCTIONS ///////////
  ////////// SAFE FUNCTIONS ///////////

  override def getHostInfo(user:User):HostInfo = {
    var server = getServer(user).getOrElse(AudioServerInfo("", "", 0))
    HostInfo(1, server.ip, server.host, server.port)
  }

  /**
   * Puts a person into the user group and the default room (no room?)
   */
  override def loginUser(user:User):Option[LoginInfo] = {
    // add the user
    getUsers(user.id) += (user.id -> user)

    // add the person to the lobby
    if(moveToRoom(user.id, lobbyRoomId)) {
      // add the view
      // get all the users in the room
      val usersInRoom:Iterable[Long] = getUserRoom(user.id)
        .filter{ case(k,v) => v.roomId == lobbyRoomId }
        .keys
        .filterNot(_ == user.id)
      val views:ViewMap = getViews(user.id)
      usersInRoom foreach { otherUserId =>
        val key1 = AudioViewKey(user.id, otherUserId)
        val key2 = AudioViewKey(otherUserId, user.id)

        List(key1,key2) foreach { key:AudioViewKey =>
          if(! views.get(key).isDefined) {
            views += (key -> AudioView(key.toId, NORMAL_LEVEL))
          }
        }

      }

      // get the ip and host
      val hostInfo = getHostInfo(user)

      // return the login info
      Some(LoginInfo(hostInfo, user))
    } else {
      None
    }
  }

  override def logout(userId:Long):Boolean = {
    // remove the links
    getUserRoom(userId) -= userId

    // remove the views
    val fromKeys:Iterable[AudioViewKey] = getViews(userId).filterKeys(k => k.fromId == userId).keys
    val toKeys:Iterable[AudioViewKey]   = getViews(userId).filterKeys(k => k.toId == userId).keys

    getViews(userId) --= (fromKeys)
    getViews(userId) --= (toKeys)

    // remove the user
    getUsers(userId) -= (userId)

    ChannelService.logout(userId)
  }

  override def loginAudio(audioLogin:AudioLogin, channel:ByteChannel):Try[AudioLogin] = {
    ChannelService.login(audioLogin.userId, channel)
    Success(audioLogin)
  }

  private def getNextRoomId(userId:Long) = getRooms(userId).foldLeft(0l){ (l:Long,r:(Long, Room)) =>
    val (key, room) = r
    if(l >= key + 1) l
    else key + 1
  }

  override def createRoom(userId:Long, name:String):Room = {
    val newRoom = Room(getNextRoomId(userId), name)
    getRooms(userId) += (newRoom.id -> newRoom)
    newRoom
  }

  override def getRoomIdForUser(userId:Long):Option[Long] = {
    getUserRoom(userId).get(userId).map(_.roomId)
  }

  override def closeRoom(userId:Long, roomId:Long):Boolean = {
    val roomToClose = getRoom(userId, roomId)

    val res = for {
      roomToClose <- getRoom(userId, roomId)
    } yield {
      logger.debug(s"Inside Yield: ${roomId}")
      logger.debug(s"User-Rooms: ${getUserRoom(userId).values.map(_.roomId).toList.mkString(",")}")
      if(getUserRoom(userId).values.map(_.roomId).toList.contains(roomId)) {
        false
      } else {
        getRooms(userId) -= (roomId)
        true
      }
    }

    res match {
      case Some(v) => v
      case _ => false
    }
  }

  override def moveToRoom(userId:Long, roomId:Long):Boolean = {
    val res = for {
      room <- getRoom(userId, roomId)
    } yield {
      val ur = getUserRoom(userId)
      ur += (userId -> UserRoom(roomId, false))
    }

    res match {
      case Some(_) => true
      case _ => false
    }
  }

  override def getAudioViewForUser(userId:Long):List[AudioView] = {
    // gets all users (not self)
    //val allUserAudioView = getViews(userId).filterKeys(_.fromId == userId).values.toList
    val allUserAudioView = getViews(userId).filterKeys(_.fromId == userId)

    // remove user not in the room
    val currentRoom = getUserRoom(userId).get(userId).map(_.roomId)
    val res = allUserAudioView filterKeys (k =>
        currentRoom == getUserRoom(userId).get(k.toId).map(_.roomId))
    //logger.debug(s"1st Views2: ${userId}=>${res}")

    res.values.toList
  }

  override def changeVolume(userId:Long, toUserId:Long, vol:Volume.Value):Option[Int] = {
    // check if audio view exists
    getView(userId, toUserId).map(_.changeVolume(vol))
  }

  override def startTalking(userId:Long, toUserId:Long):Boolean = {
    val ur = getUserRoom(userId)
    val res = for {
      ab <- ur.get(userId)
      ba <- ur.get(toUserId)
    } yield {
      if(ab.roomId == ba.roomId) {
        ab.isTalking = true
        ba.isTalking = true
        true
      } else {
        false
      }
    }

    res match {
      case Some(v) => v
      case _ => false
    }
  }

  override def stopTalking(userId:Long):Boolean = {
    val ur = getUserRoom(userId)
    ( ur.get(userId) map { ab =>
        ab.isTalking = false
        true
    } ) match {
      case Some(v) => v
      case _ => false
    }
  }

  override def whosTalking(userId:Long):List[Long] = {
    val ur = getUserRoom(userId)
    ur.get(userId).map{ currRoom =>
      val urList:UserRoomMap = ur.filter{ case(k,v) => v.roomId == currRoom.roomId && v.isTalking == true }
      urList.keys.toList
    }.getOrElse(List[Long]())
  }

  override def listRooms(userId:Long):List[Room] = {
    getRooms(userId).values.toList
  }

}



// /**
//  * Temporary dataservice for volatile objects.  Should be replaced with cache such as redis/hazelcast/...
//  */
// object DataService {
//   private val logger = LoggerFactory.getLogger(getClass)
//
//   /**
//    * Creates the empty lobby room
//    */
//   private val lobby = Room(lobbyRoomId, "Lobby", mutable.Map[Long, User]())
//   private val roomA = Room(roomAId, "Room A", mutable.Map[Long, User]())
//
//   // all rooms
//   @volatile private var _rooms = mutable.Map[Long, Room](lobbyRoomId -> lobby, roomAId -> roomA)
//
//   // all users
//   @volatile private var _users = mutable.Map[Long, User]()
//
//   // all sockets
//   @volatile private var _sockets = mutable.Map[Long, Socket]()
//
//   // all channels
//   @volatile private var _channels = mutable.Map[Long, SocketChannel]()
//
//   // views are rooms and users on the user's perspective
//   @volatile private var _views = mutable.Map[Long, View]()
//
//   // all buffers.  this belongs on the audioserver
//   @volatile private var _buffers = mutable.Map[Long, CircularByteBuffer]()
//
//   // all servers, prepare for future where this is many
//   private var _server:Option[AudioServerInfo] = None
//
//   //////////////// testing implementations only ///////////
//   // MISSING SECURITY
//
//   def hash(userId:Long) = "#"
//
//   def getUsers(userId:Long):mutable.Map[Long, User] = _users
//
//   def getUser(userId:Long):Option[User] = _users.get(userId)
//
//   def getViews(userId:Long):mutable.Map[Long, View] = _views
//
//   def getView(userId:Long):Option[View] = _views.get(userId)
//
//   def getRoom(user:User, roomId:Long):Option[Room] = getRooms(user).get(roomId)
//
//   def getRooms(user:User):mutable.Map[Long, Room] = _rooms
//
//   def getServer(user:User):Option[AudioServerInfo] = _server
//
//   def getBuffer(userId:Long):CircularByteBuffer = _buffers(userId)
//
//   def getHostInfo(user:User):HostInfo = {
//     var server = getServer(user).getOrElse(AudioServerInfo("", "", 0))
//     HostInfo(1, server.ip, server.host, server.port)
//   }
//
//   def getRoomIdForUserId(userId:Long):Long = {
//     getView(userId).get.web.currentRoom.id // hack
//   }
//
//   def getAudioBufForUser(userId:Long):CircularByteBuffer = {
//     getBuffer(userId)
//   }
//
//   def registerServer(audioServer:AudioServerInfo):Unit = {
//     _server = Some(audioServer)
//   }
//
//
//
//
//
//   ///////////////// WORKs BUT THIS IS REALLY A DUMMY IMPL ///////////////
//   // NEED TO REMOVE access to the underscore vals before it can graduate
//   def loginAudioUser(audioLogin:Option[AudioLogin], socket:Option[Socket], channel:Option[SocketChannel]):Try[AudioLogin] = {
//     audioLogin match {
//       case Some(audioLogin) =>
//         // TODO: do something with the hash
//         // TODO: Can be mis-matched if user changes rooms before audio becomes available
//
//         // get the ppl in the lobby
//         val user = getUser(audioLogin.userId).get //hack!
//         val pplInLobby = getPeopleInRoomId(user, lobbyRoomId)
//
//         (socket, channel) match {
//           case (Some(socket), _) => _sockets += (audioLogin.userId -> socket)
//           case (_, Some(channel)) => _channels += (audioLogin.userId -> channel)
//           case _ => Unit
//         }
//
//         _buffers += (audioLogin.userId -> CircularByteBuffer.newBuf(audioLogin.userId.toInt))
//
//         Success(audioLogin)
//       case _ => Failure(new IllegalArgumentException("audioLogin is None"))
//     }
//   }
//
//   def getAudioViewForUser(userId:Long):AudioView = {
//     val roomId = getRoomIdForUserId(userId)
//     val user = getUser(userId).get // hack!
//     val theUsers = getPeopleInRoomId(user, roomId)
//     val people:List[AudioUser] = theUsers.flatMap { case (k, u) =>
//       for {
//         buf <- _buffers.get(u.id)
//       } yield {
//         val s = _sockets.get(u.id)
//         val c = _channels.get(u.id)
//         AudioUser(k, s, c, buf, 5, u.hash)
//       }
//     }.toList
//
//     //HACK!
//     AudioView(userId, getRoom(user, roomId).get, people)
//   }
//
//
//
//
//
//
//
//   ///////////////// WORK BEGINS ///////////////
//
//   /**
//    * Puts a person into the user group and the default room (no room?)
//    */
//   def login(user:User):Option[LoginInfo] = {
//     //TODO: do some security thing here
//
//     // Add the user to the list.
//     getUsers(user.id) += (user.id -> user)
//
//     // add the person to the lobby
//     addPersonToRoom(lobbyRoomId, user)
//
//     // Create the Login Info.  Normally this should be based on Domain Info.  Basically we need to put the people
//     // in the same domain on the same server.  For larger instances this should break down into groups as well.
//     val webView = WebView(user.id, lobby.toWebRoom, user.hash)
//
//     val view = View(webView, None)
//
//     // add the view
//     getViews(user.id) += (user.id -> view)
//
//     // get the ip and host
//     val hostInfo = getHostInfo(user)
//
//     // return the login info
//     Some(LoginInfo(hostInfo, webView))
//   }
//
//
//   def updateView(userId:Long, view:View):Boolean = {
//     Try(getViews(userId) += (userId -> view)) match {
//       case Success(_) => true
//       case Failure(e) =>
//         e.printStackTrace
//         false
//     }
//   }
//
//   def moveToRoom(userId:Long, roomId:Long):Boolean = {
//     logger.debug(s"moving $userId to $roomId")
//
//     // validate(roomId, userId)
//     val userOpt = getUser(userId)
//
//     userOpt.map { user =>
//       movePersonToRoom(roomId, user)
//     } match {
//       case Some(v) => v
//       case _ => false
//     }
//   }
//
//   private def movePersonToRoom(roomId:Long, user:User):Boolean = {
//     removePersonFromRoom(user) && addPersonToRoom(roomId, user) && changeView(roomId, user)
//   }
//
//   private def changeView(roomId:Long, user:User):Boolean = {
//     val res = for {
//       view <- getView(user.id)
//       room <- getRoom(user, roomId)
//     } yield {
//       val webRoom = room.toWebRoom
//       val webC = view.web.copy(currentRoom = webRoom)
//       val audioC = view.audio.map(_.copy(currentRoom = room))
//       val newView = View(webC, audioC)
//
//       updateView(user.id, newView)
//     }
//
//     res match {
//       case Some(v) => v
//       case _ => false
//     }
//   }
//
//   private def removePersonFromRoom(user:User):Boolean = {
//     val rooms = getRooms(user)
//
//     rooms.values.map{  r =>
//       val pSize1 = r.people.size
//       r.people.remove(user.id)
//       val pSize2 = r.people.size
//
//       if(pSize2 != pSize2) {
//         println(s"Removed User from ${r.id}")
//       }
//     }
//
//     true
//   }
//
//   private def addPersonToRoom(roomId:Long, user:User):Boolean = {
//     val roomOpt = getRoom(user, roomId)
//
//     roomOpt match {
//       case Some(room) =>
//         if(!room.people.contains(user.id)) {
//           room.people += (user.id -> user)
//         }
//         true
//       case _ => false
//     }
//   }
//
//   def getPeopleInRoomId(user:User, roomId:Long):Map[Long, User] = {
//     val roomOpt = getRoom(user, roomId)
//
//     roomOpt match {
//       case Some(room) => room.people.toMap
//       case _ => Map[Long,User]()
//     }
//   }
//
//
//   def listRooms(userId:Long):List[WebRoom] = {
//     getUser(userId) match {
//       case Some(user) => getRooms(user).values.map(_.toWebRoom).toList
//       case _ => List[WebRoom]()
//     }
//   }
//
//
// }
