package together

import org.specs2._
import org.specs2.specification.Scope
import java.nio._
import java.nio.channels._
import java.net._
import together.data._
import together.audio._
import together.audio.Conversions._


class ChannelServiceSpec extends mutable.Specification {
  class TestChannel(marker:String) extends ByteChannel {
    val wBuffer = ByteBuffer.allocate(bufferLengthInBytes)
    val rBuffer = ByteBuffer.allocate(bufferLengthInBytes)

    def close(): Unit = ???
    def isOpen(): Boolean = ???

    def read(r:java.nio.ByteBuffer): Int = {
      r.put(rBuffer.array())
      rBuffer.array().length
    }
    def write(w:java.nio.ByteBuffer): Int = {
      println(s"Write Invoked:$marker:${Conversions.checksum(w.array())}")
      wBuffer.clear()
      wBuffer.put(w.array())
      w.position(w.capacity())
      w.array().length
    }

    def clientReadWrite(w:java.nio.ByteBuffer):Unit = {
      rBuffer.clear()
      rBuffer.put(w.array())
    }

    def clientWriteRead(w:java.nio.ByteBuffer):Unit = {
      w.put(wBuffer.array())
    }

    def flush():Unit = {
      val empty:Array[Byte] = Array.ofDim[Byte](bufferLengthInBytes)
      wBuffer.clear();
      wBuffer.put(empty);
      wBuffer.clear();

      rBuffer.clear()
      rBuffer.put(empty);
      rBuffer.clear();
    }
  }

  val channelService = ServiceLocator.channelService
  val ds = ServiceLocator.dataService

  ds.registerServer(AudioServerInfo("127.0.0.1", "localhost", 55555))

  val u1 = User(1, "User 1", 1, 1, "#")
  val u2 = User(2, "User 2", 1, 1, "#")
  val u3 = User(3, "User 3", 1, 1, "#")

  val l1 = ds.loginUser(u1)
  val l2 = ds.loginUser(u2)
  val l3 = ds.loginUser(u3)

  val c1 = new TestChannel("c1")
  val c2 = new TestChannel("c2")
  val c3 = new TestChannel("c3")

  val a1 = AudioLogin(1, "#")
  val a2 = AudioLogin(2, "#")
  val a3 = AudioLogin(3, "#")

  var ct = 0
  var room3:Room = null
  var u2Av:List[AudioView] = null

  sequential

  "Given fulltime listen server" should {
    "Setup User 1 2 and 3" in {
      ok
    }

    "When Test Audio Login User 1,2 and 3" in {
      "Then login should not fail" in {
        channelService.login(a1, c1)
        channelService.login(a2, c2)
        channelService.login(a3, c3)
        ok
      }

      "Sending 1s on a1 should send 1 * .1 on other channels since they're not talking" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(.1f)).foreach
        c3Floats.toSeq must contain(be_==(.1f)).foreach
      }
    }


    // 1. Change rooms should result in one of the channels getting nothing
    "When Test User 2 move to room A" in {
      "Move room should not throw" in {
        ds.moveToRoom(u2.id, roomAId)
        ok
      }

      "Sending 1s on a1 should send 1 * .1 on 3rd channel since they're not talking" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(0f)).foreach
        c3Floats.toSeq must contain(be_==(.1f)).foreach
      }

    }

    "move user 1 to room A." in {
      "Move room should not throw" in {
        ds.moveToRoom(u1.id, roomAId)
        ok
      }

      "Should result in .1 on 2 and 0 on 3"  in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(.1f)).foreach
        c3Floats.toSeq must contain(be_==(0f)).foreach
      }
    }

    "move user 3 to room A" in {
      "Move room should not throw" in {
        ds.moveToRoom(u3.id, roomAId)
        ok
      }
      "Should result in .1 on 2 and .1 on 3" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(.1f)).foreach
        c3Floats.toSeq must contain(be_==(.1f)).foreach
      }
    }

    "setting user 1 and user 2 to talking" in {
      "Set to talking" in {
        ds.startTalking(u1.id, u2.id)
        ok
      }
      "should result in 1 on 2 and .1 on 3" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(1f)).foreach
        c3Floats.toSeq must contain(be_==(.1f)).foreach
      }
    }

    "setting user 1 2 view to +1" in {
      "increase volume" in {
        val res = ds.changeVolume(u2.id, u1.id, Volume.UP)
        val u2Av = ds.getAudioViewForUser(u2.id)
        res must beSome(6)
        u2Av.get(u1.id).map(_.streamFctr) must beSome((1.2f))
        ok
      }
      "should result in 1*6/5 for 2 and .1 on 3" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==((6f/5))).foreach
        c3Floats.toSeq must contain(be_==(.1f)).foreach
      }
    }

    "setting user 1 and user 3 to talking" in {
      "Set to talking" in {
        ds.startTalking(u1.id, u3.id)
        ok
      }
      "should result in 6/5 on 2 and 1 on 3" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==((6f/5))).foreach
        c3Floats.toSeq must contain(be_==(1f)).foreach
      }
    }

    "setting user 2 to not talking" in {
      "Set to talking" in {
        ds.stopTalking(u2.id)
        ok
      }
      "should result in .1 on 2 and 1 on 3" in {
        // resets the streams
        c1.flush()
        c2.flush()
        c3.flush()
        val arr = toFloatArrayFill(bufferLengthInBytes, 1f)
        val buf = ByteBuffer.wrap(arr)
        c1.clientReadWrite(buf)

        channelService.tick

        // read from buffer
        val c2Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c2.clientWriteRead(c2Buf)

        val c3Buf = ByteBuffer.allocate(bufferLengthInBytes)
        c3.clientWriteRead(c3Buf)

        val c2Floats:Array[Float] = Conversions.toFloatArray(c2Buf.array())
        val c3Floats:Array[Float] = Conversions.toFloatArray(c3Buf.array())

        c2Floats.toSeq must contain(be_==(.1f)).foreach
        c3Floats.toSeq must contain(be_==(1f)).foreach
      }
    }

    // setting user 1 2 view to +1 should result in 1*7/5 for 2 and .1 on 3
    // setting user 1 2 view to +1 should result in 1*8/5 for 2 and .1 on 3
    // setting user 1 2 view to +1 should result in 1*9/5 for 2 and .1 on 3
    // setting user 1 2 view to +1 should result in 1*10/5 for 2 and .1 on 3
    // setting user 1 2 view to +1 should result in 1*10/5 for 2 and .1 on 3

    // login, logout

  }

  "Shutdown" in {
    println("SHUTDOWN")
    channelService.shutdown()
    ok
  }

}
