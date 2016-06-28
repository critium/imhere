package together

import org.specs2._
import org.specs2.specification.Scope
import java.nio._
import java.nio.channels._
import java.net._
import together.data._
import together.audio._
import together.audio.Conversions._


class DataServiceSpec extends mutable.Specification {
  class TestChannel extends ByteChannel {
    val bb = ByteBuffer.allocate(bufferLengthInBytes)

    def close(): Unit = ???
    def isOpen(): Boolean = ???
    def read(b:java.nio.ByteBuffer): Int = {
      b.put(bb.array())
      bb.array().length
    }
    def write(w:java.nio.ByteBuffer): Int = {
      bb.put(w.array())
      w.array().length
    }
  }

  println("1")
  val channelService = ServiceLocator.channelService
  println("1")
  val ds = ServiceLocator.dataService.asInstanceOf[DataServiceImpl]
  println("1")

  println("1")
  ds.registerServer(AudioServerInfo("127.0.0.1", "localhost", 55555))
  println("1")

  val u1 = User(1, "User 1", 1, 1, "#")
  val u2 = User(2, "User 2", 1, 1, "#")
  val u3 = User(3, "User 3", 1, 1, "#")

  val l1 = ds.loginUser(u1)
  val l2 = ds.loginUser(u2)
  val l3 = ds.loginUser(u3)

  val c1 = new TestChannel
  val c2 = new TestChannel
  val c3 = new TestChannel

  val a1 = AudioLogin(1, "#")
  val a2 = AudioLogin(2, "#")
  val a3 = AudioLogin(3, "#")

  var ct = 0
  var room3:Room = null
  var u2Av:List[AudioView] = null


  sequential

  "Given" should {
    "When Test Login User 1, 2 and 3" in {
      val u1av = ds.getAudioViewForUser(u1.id)
      val u2av = ds.getAudioViewForUser(u2.id)
      val u3av = ds.getAudioViewForUser(u3.id)

      println("views:" + ds.getViews(u1.id))

      "Then user table should be populated" in {
        ds.getUsers(u1.id).size must equalTo(3)
      }

      "Then  userroom should be populated" in {
        ds.getUserRoom(u1.id).get(u1.id).map(_.roomId) must beSome(lobbyRoomId)
        ds.getUserRoom(u1.id).get(u2.id).map(_.roomId) must beSome(lobbyRoomId)
      }

      "Then user view should be populated" in {
        u1av.size must equalTo (2)
        u2av.size must equalTo (2)
        u3av.size must equalTo (2)
      }

    }

    "When Test Audio Login User 1,2 and 3" in {
      "Then get channels should equal to 1 when user1 logs in" in {
        channelService.login(a1, c1)
        channelService.getChannels(a1.userId).size must equalTo(1)
      }

      "Then get channels should equal to 2 when user2 logs in " in {
        channelService.login(a2, c2)
        channelService.getChannels(a2.userId).size must equalTo(2)
      }

      "Then get channels should equal to 3 when user3 logs in " in {
        channelService.login(a3, c3)
        channelService.getChannels(a3.userId).size must equalTo(3)
      }

    }

    "When Test User 1 move to room A" in {
      "Move room should not throw" in {
        ds.moveToRoom(u1.id, roomAId)
        ok
      }

      "Then userroom should be populated" in {
        println("views:" + ds.getViews(u1.id))
        ds.getUserRoom(u1.id).get(u1.id).map(_.roomId) must beSome(roomAId)
        ds.getUserRoom(u1.id).get(u2.id).map(_.roomId) must beSome(lobbyRoomId)
        ds.getUserRoom(u1.id).get(u3.id).map(_.roomId) must beSome(lobbyRoomId)
      }

      "Then user view should be populated" in {
        val u1av = ds.getAudioViewForUser(u1.id)
        val u2av = ds.getAudioViewForUser(u2.id)
        val u3av = ds.getAudioViewForUser(u3.id)
        u1av.size must equalTo (0)
        u2av.size must equalTo (1)
        u3av.size must equalTo (1)
      }

    }

    "Test User 2 move to room A" in {
      "Move room should not throw" in {
        ds.moveToRoom(u2.id, roomAId)
        ok
      }

      "Then userroom should be populated" in {
        println("views:" + ds.getViews(u1.id))
        ds.getUserRoom(u1.id).get(u1.id).map(_.roomId) must beSome(roomAId)
        ds.getUserRoom(u1.id).get(u2.id).map(_.roomId) must beSome(roomAId)
        ds.getUserRoom(u1.id).get(u3.id).map(_.roomId) must beSome(lobbyRoomId)
      }

      "Then user view should be populated" in {
        val u1av = ds.getAudioViewForUser(u1.id)
        val u2av = ds.getAudioViewForUser(u2.id)
        val u3av = ds.getAudioViewForUser(u3.id)
        u1av.size must equalTo (1)
        u2av.size must equalTo (1)
        u3av.size must equalTo (0)
      }
    }

    "When Test User 2 list rooms" in {
      "list should have 2 rooms" in {
        val rooms = ds.listRooms(u2.id)
        rooms.size must equalTo(2)
      }
    }

    "When Test User 3 create new room B" in {
      "Create Room Should Not Throw" in {
        room3 = ds.createRoom(u3.id, "room B")
        room3.name must equalTo("room B")
      }

      "list should have 3 rooms" in {
        val rooms = ds.listRooms(u2.id)
        rooms.size must equalTo(3)
      }
    }

    "When Test User 2 and 3 move to room B" in {
      "Move room should not throw" in {
        ds.moveToRoom(u2.id, room3.id)
        ds.moveToRoom(u3.id, room3.id)
        ok
      }

      "Then userroom should be populated" in {
        println("views:" + ds.getViews(u1.id))
        ds.getUserRoom(u1.id).get(u1.id).map(_.roomId) must beSome(roomAId)
        ds.getUserRoom(u1.id).get(u2.id).map(_.roomId) must beSome(room3.id)
        ds.getUserRoom(u1.id).get(u3.id).map(_.roomId) must beSome(room3.id)
      }

      "Then user view should be populated" in {
        val u1av = ds.getAudioViewForUser(u1.id)
        val u2av = ds.getAudioViewForUser(u2.id)
        val u3av = ds.getAudioViewForUser(u3.id)
        u1av.size must equalTo (0)
        u2av.size must equalTo (1)
        u3av.size must equalTo (1)
      }

      "list should have 3 rooms" in {
        val rooms = ds.listRooms(u2.id)
        rooms.size must equalTo(3)
      }
    }

    "When Test User 2 close room A" in {
      "Move room should not throw" in {
        ds.moveToRoom(u2.id, lobbyRoomId)
        ds.moveToRoom(u3.id, lobbyRoomId)
        val res = ds.closeRoom(u3.id, room3.id)
        res should equalTo(true)
      }

      "Then userroom should be populated" in {
        ds.getUserRoom(u1.id).get(u1.id).map(_.roomId) must beSome(roomAId)
        ds.getUserRoom(u1.id).get(u2.id).map(_.roomId) must beSome(lobbyRoomId)
        ds.getUserRoom(u1.id).get(u3.id).map(_.roomId) must beSome(lobbyRoomId)
      }

      "Then user view should be populated" in {
        val u1av = ds.getAudioViewForUser(u1.id)
        val u2av = ds.getAudioViewForUser(u2.id)
        val u3av = ds.getAudioViewForUser(u3.id)
        u1av.size must equalTo (0)
        u2av.size must equalTo (1)
        u3av.size must equalTo (1)
      }

      "list should have 2 rooms" in {
        val rooms = ds.listRooms(u2.id)
        rooms.size must equalTo(2)
      }
    }

    "When Test User 2 change set talk to 1 (not in room)" in {
      "Start Talking Should be false" in {
        val res = ds.startTalking(u2.id, u1.id)
        res must equalTo(false)
      }

      "whos talking should be 0" in {
        val whosTalking = ds.whosTalking(u2.id)
        whosTalking.size must equalTo(0)
      }
    }

    "When Test User 2 change set talk to 3" in {
      "Start Talking Should be true" in {
        val res = ds.startTalking(u2.id, u3.id)
        res must equalTo(true)
      }

      "whos talking should be 2" in {
        val whosTalking = ds.whosTalking(u2.id)
        whosTalking.size must equalTo(2)
        whosTalking must contain(u2.id)
        whosTalking must contain(u3.id)
      }
    }

    "When Test User 2 leave talk" in {
      "Leaving should return true" in {
        val res = ds.stopTalking(u2.id)
        res must equalTo(true)
      }

      "whos talking should be 1" in {
        val whosTalking = ds.whosTalking(u2.id)
        whosTalking.size must equalTo(1)
        whosTalking must contain(u3.id)
      }
    }

    // NOT SURE ABOUT THE VALUE OF THIS.  COMMENTING OUT FOR NOW.
    // "When Test User 2 set talk to self" in  {
    //   "Start Talking Should be true" in {
    //     val res = ds.startTalking(u2.id, u3.id)
    //     res must equalTo(true)
    //   }

    //   "whos talking should be 2" in {
    //     val whosTalking = ds.whosTalking(u2.id)
    //     whosTalking.size must equalTo(2)
    //     whosTalking must contain(u2.id)
    //     whosTalking must contain(u3.id)
    //   }
    // }

    "When Test User 2 change User 3 Volume UP" in {
      "user 3 volume should start at 5" in {
        u2Av = ds.getAudioViewForUser(u2.id)
        println("U2AV: " + u2Av.mkString(","))
        u2Av.find(_.userId == u3.id).map(_.volume) must beSome(5)
      }
      "user 3 volume should end at 6" in {
        val res = ds.changeVolume(u2.id, u3.id, Volume.UP)
        res must beSome(6)
      }

      "user 3 volume should end at 7" in {
        val res = ds.changeVolume(u2.id, u3.id, Volume.UP)
        res must beSome(7)
        u2Av.find(_.userId == u3.id).map(_.volume) must beSome(7)
      }
    }

    "When Test User 2 change User 1 Volume UP to MAX" in {
      "user 3 volume should stop at 10" in {
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        ds.changeVolume(u2.id, u3.id, Volume.UP)
        val res = ds.changeVolume(u2.id, u3.id, Volume.UP)
        res must beSome(10)
        u2Av.find(_.userId == u3.id).map(_.volume) must beSome(10)
      }
    }

    "When Test User 2 change User 1 Volume DOWN" in {
      "user 3 volume should go back to 9" in {
        val res = ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        res must beSome(9)
        u2Av.find(_.userId == u3.id).map(_.volume) must beSome(9)
      }
    }

    "When Test User 2 change User 1 Volume DOWN to MIN" in {
      "user 3 volume should go back to 9" in {
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        val res = ds.changeVolume(u2.id, u3.id, Volume.DOWN)
        res must beSome(0)
        u2Av.find(_.userId == u3.id).map(_.volume) must beSome(0)
      }
    }

    "When Test User 1 logout" in {
      "do it" in {
        ds.getUserRoom(u1.id).get(u1.id) should beSome
        ds.getAudioViewForUser(u2.id).size must be_>=(1)
        ds.getAudioViewForUser(u3.id).find(_.userId == u2.id) should beSome
        ds.getUsers(u3.id).get(u2.id) should beSome
        ds.logout(u2.id)
      }

      "user room should remove it" in {
        ds.getUserRoom(u2.id).get(u2.id) should beNone
      }

      "views should remove it" in {
        ds.getAudioViewForUser(u2.id).size must be_==(0)
        ds.getAudioViewForUser(u3.id).find(_.userId == u2.id) should beNone
      }

      "users should remove it" in {
        ds.getUsers(u3.id).get(u2.id) should beNone
      }
    }

    // channels and views ?????

    // serverside passive vs active listen
  }

  channelService.shutdown()
}
