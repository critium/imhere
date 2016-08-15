package together

import org.specs2._
import org.specs2.specification.Scope
import java.nio._
import java.nio.channels._
import java.net._
import together.data._
import together.audio._
import together.audio.Conversions._

import java.io._
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import com.sun.media.sound._


class ConversionSpec extends mutable.Specification {

  sequential

  "Clip Function" should {
    "Should NOT cip if i = 100" in {
      val i = 100
      Conversions.clipInt16(i) should equalTo(i)
    }
    "Should NOT cip if i = -100" in {
      val i = -100
      Conversions.clipInt16(i) should equalTo(i)
    }
    "Should cip if i = 34000" in {
      val i = 34000
      Conversions.clipInt16(i) should equalTo(CLIP_PCM)
    }
    "Should cip if i = -34000" in {
      val i = -34000
      Conversions.clipInt16(i) should equalTo(-1 * CLIP_PCM)
    }
  }

  "Gain Calculation" should {
    "Equal to 004 given 0" in {
      val res = Conversions.calculateGain(0)
      val tgt = .00398f
      Math.abs(res-tgt) should be_<=(.0001f)
    }
    "Equal to 1.99526 given 0" in {
      val res = Conversions.calculateGain(100f/100)
      val tgt = 1.99526f
      println("&&" + res + " " + tgt)
      Math.abs(res-tgt) should be_<=(.0001f)
    }
  }

  "pcmToInt16" should {
    "Convert to " in {
      val b = Array[Byte](0x01, 0x02, 0x0F, 0x0F)
      val res:Int = pcmToInt16(b, 0, true)
      val res2:Int = pcmToInt16(b, 2, true)

      println("**" + res + ":" + res.toBinaryString + " res2: " + res2)
      for(i <- 0 until (b.length)){
        print(Integer.toBinaryString(b(i) & 0xFF))
      }
      println("")


      val b2 = Array[Byte](0,0,0,0)
      int16ToPCM(res, b2, 0, true)
      int16ToPCM(res2, b2, 2, true)

      println("back")
      for(i <- 0 until b2.length){
        print(Integer.toBinaryString(b2(i) & 0xFF))
      }
      println("")
      ok
    }
  }


}
