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
  private val logger = LoggerFactory.getLogger(getClass)

  var encoding = AudioFormat.Encoding.PCM_SIGNED;
  // TODO: Decrease this to 8000
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
  val bufferLengthInSeconds = .1
  val bufferLengthInBytes:Int = ((rate * bufferLengthInSeconds) * (floatLen / byteLen)).toInt
  val bufferBarrier = 128 // this is much too large, but lets see
  val bufferCheck = 10;

  val MAX_VOL = 10
  val MIN_VOL = 0
  val SILENT_FCTR = .1
  val NORMAL_LEVEL = ((MAX_VOL + MIN_VOL) / 2).toInt

  def getAudioFormat:AudioFormat = {
    logger.debug("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }

}
