package together.data

import together.audio.AudioServer._
import together.audio.AudioServer.RelayServer

import scala.collection.mutable
import scala.util._

/**
 * Temporary dataservice for volatile objects.  Should be replaced with cache such as redis/hazelcast/...
 */
object DataService {

  /**
   * Creates the empty lobby room
   */
  private val lobby = Room(lobbyRoomId, "Lobby", mutable.Map[Long, User]())

  // all rooms
  @volatile private var _rooms = mutable.Map[Long, Room](lobbyRoomId -> lobby)

  // all users
  @volatile private var _users = mutable.Map[Long, User]()

  // views are rooms and users on the user's perspective
  @volatile private var _views = mutable.Map[Long, View]()


  /**
   * Puts a person into the user group and the default room (no room?)
   */
  def login(user:User):Option[LoginInfo] = {
    //TODO: do some security thing here

    // Add the user to the list.
    _users += (user.id -> user)

    // add the person to the lobby
    addPersonToRoom(lobbyRoomId, user)

    // Create the Login Info.  Normally this should be based on Domain Info.  Basically we need to put the people
    // in the same domain on the same server.  For larger instances this should break down into groups as well.
    val webView = WebView(user.id, lobby, getPeopleInRoomId(user, lobby.id))

    val view = View(webView, None)

    // add the view
    _views += (user.id -> view)

    // get the ip and host
    val hostInfo = getHostInfo(user)

    // return the login info
    Some(LoginInfo(hostInfo, webView))
  }

  def addPersonToRoom(roomId:Long, user:User):Boolean = {
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

  def getRoom(user:User, roomId:Long):Option[Room] = {
    var rooms:mutable.Map[Long, Room] = getRooms(user)

    rooms.get(roomId)
  }

  /**
   * Hardcoded for now...
   */
  def getRooms(user:User):mutable.Map[Long, Room] = {
    _rooms
  }

  /**
   * Hardcoded for now...
   */
  def getHostInfo(user:User):HostInfo  ={
    HostInfo(1, RelayServer.ip, RelayServer.host, RelayServer.port)
  }

  /**
   * Login the user from the audio socket
   * DUMMY ONLY
   */
  def loginAudioUser(audioUser:Option[AudioUser]):Try[AudioView] = {
    val av = AudioView(0, lobby, List[AudioUser]())
    Success(av)
  }
}
