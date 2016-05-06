package together

import together.data._
import together.audio.Conversions._

import java.io._
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.util._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import com.sun.media.sound._

import org.slf4j.LoggerFactory

package object audio {
  var encoding = AudioFormat.Encoding.PCM_SIGNED;
  val rate = 18000f
  val sampleSize = 16
  val bigEndian = true
  val channels = 1
  val voiceLow = 300f
  val voiceHigh = 3000f
  val voiceResonance = (voiceLow + voiceHigh) / 2
  val voiceFrequency = voiceHigh - voiceLow
  val floatLen = 32;
  val byteLen = 8
  //val bufferLengthInBytes = 64
  val bufferLengthInBytes:Int = (rate * .1 * floatLen / byteLen).toInt
  val bufferBarrier = 128 // this is much too large, but lets see
  val bufferCheck = 10;
}
