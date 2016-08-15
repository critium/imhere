package together

import together.data._
import together.audio._
import together.audio.Conversions._

import java.io._
import java.net._
import java.nio._
import java.nio.channels._

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.util._
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

import com.sun.media.sound._

import org.slf4j.LoggerFactory

object PlayTest {
  private val logger = LoggerFactory.getLogger(getClass)

  val format = getAudioFormat;

  def playback(stream:Array[Byte])= {
    val fileStream = new ByteArrayInputStream(stream)
    val info = new DataLine.Info(classOf[SourceDataLine], format);
    val line:SourceDataLine = AudioSystem.getLine(info).asInstanceOf[SourceDataLine]
    line.open(format, bufferLengthInBytes);

    //val bufferLengthInFrames = line.getBufferSize() / 8;
    //val bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
    val data = Array.ofDim[Byte]( bufferLengthInBytes );
    var numBytesRead = 0;

    // start the source data line
    line.start();

    val bytes = Array.ofDim[Byte](bufferLengthInBytes)
    var keeprunning = false
    while (fileStream.available > 0) {
      keeprunning = true
      fileStream.read(bytes)

      val playBytes = if(fileStream.available == 0 ) {
        Array.ofDim[Byte](bufferLengthInBytes)
      } else {
        bytes
      }
      val baos = new ByteArrayInputStream(playBytes)

      //val baos = new ByteArrayInputStream(bytes)
      //val audioInputStream = new AudioInputStream(baos, format, fileLength / frameSizeInBytes);
      val audioInputStream = new AudioInputStream(baos, format, bufferLengthInBytes);
      val playbackInputStream = AudioSystem.getAudioInputStream(format, audioInputStream);

      while (keeprunning) {
        val numBytesRead = playbackInputStream.read(data)
        if (numBytesRead == -1) {
          keeprunning = false
        } else {
          line.write(data, 0, numBytesRead)
        }
      }
    }

    println("Ending Playback")

    // we reached the end of the stream.  let the data play out, then
    // stop and close the line.
    line.drain();
    line.stop();
    line.close();
    //line = null;
  }

  def runTests(tests:List[(String, () => Unit)]) = {
    tests.foreach(runTest(_))
  }

  def runTest(test:(String, () => Unit)) = {
    logger.debug("Running test: " + test._1)
    test._2()
  }

  def printBytes(b:Array[Byte]):String = {
    val sb = new StringBuilder()
    for(i <- 0 until b.length){
      //if(i>0 && i%2==0) sb.append(" ")
      sb.append(" ")
      sb.append(Integer.toBinaryString((b(i) & 0xFF) + 0x100).substring(1))
    }
    sb.toString()
  }

  val tests:List[(String, () => Unit)] = List(
    ("PlaybackTest",  () => {
      val testFile = "../samples/test1.raw"
      val f = new File(testFile)
      val fis = new FileInputStream(f)
      val frameSizeInBytes = format.getFrameSize();
      val numBytes = rate * frameSizeInBytes * 1
      val stream = Array.ofDim[Byte](numBytes.toInt)

      for(i <- 0 until stream.length) {
        stream(i) = fis.read().toByte
      }

      playback(stream)
    }),

    ("PCM to int 16 and back",  () => {
      val testFile = "../samples/test1.raw"
      val f = new File(testFile)
      val fis = new FileInputStream(f)
      val frameSizeInBytes = format.getFrameSize();
      val numBytes = rate * frameSizeInBytes * 1
      val stream = Array.ofDim[Byte](numBytes.toInt)

      for(i <- 0 until stream.length) {
        stream(i) = fis.read().toByte
      }
      //logger.debug("INPUT: " + stream.length + ":" + printBytes(stream))

      val intStream = Array.ofDim[Int](stream.length/2)
      for(i <- 0 until intStream.length ) {
        val pos = i*2;
        intStream(i) = pcmToInt16(stream, pos, bigEndian)
      }

      //logger.debug("INT: " + intStream.mkString(","))

      val outStream = Array.ofDim[Byte](numBytes.toInt)
      for(i <- 0 until intStream.length ) {
        val pos = i*2;
        //val bytePair = int16ToPCM(intStream(i), 0, bigEndian)
        int16ToPCM(intStream(i), outStream, pos, bigEndian)
        //outStream(pos) = bytePair(0)
        //outStream(pos+1) = bytePair(1)
      }
      //logger.debug("OUTPUT: " + outStream.length + ":" + printBytes(outStream))

      playback(outStream)
    }),

    ("PCM increase Gain by 6/5",  () => {
      val testFile = "../samples/test1.raw"
      val f = new File(testFile)
      val fis = new FileInputStream(f)
      val frameSizeInBytes = format.getFrameSize();
      val numBytes = rate * frameSizeInBytes * 1
      val stream = Array.ofDim[Byte](numBytes.toInt)

      val gain = calculateGain(9/5)

      for(i <- 0 until stream.length) {
        stream(i) = fis.read().toByte
      }
      //logger.debug("INPUT: " + stream.length + ":" + printBytes(stream))

      val intStream = Array.ofDim[Int](stream.length/2)
      for(i <- 0 until intStream.length ) {
        val pos = i*2;
        intStream(i) = (pcmToInt16(stream, pos, bigEndian) * gain).toInt
      }

      //logger.debug("INT: " + intStream.mkString(","))

      val outStream = Array.ofDim[Byte](numBytes.toInt)
      for(i <- 0 until intStream.length ) {
        val pos = i*2;
        int16ToPCM(intStream(i), outStream, pos, bigEndian)
      }
      //logger.debug("OUTPUT: " + outStream.length + ":" + printBytes(outStream))

      playback(outStream)
    })
  )

  def main(args:Array[String]):Unit = {
    logger.debug("Runing test")
    runTests(tests)
  }


}


