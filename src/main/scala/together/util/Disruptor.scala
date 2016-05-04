package together.util

import java.nio._
import together.audio._

//import com.lmax.disruptor.dsl.Disruptor
//import java.util.concurrent.Executors
//import com.lmax.disruptor._

//case class BufferEvent (b:ByteBuffer)

//case class ValueEventTranslator(value: Long) extends EventTranslator[ValueEvent] {
  //def translateTo(event: ValueEvent, sequence: Long) = {
    //event.value = value
    //event
  //}
//}


/**
 * 1 disruptor for all streams
 * 1 event handler for each rooms to mix and send
 */
//class Disruptor {
  //def testBuffer():ByteBuffer = {
    //val testString:String = ""
    //val testStringAsByte:Array[Byte] = testString.toBytes
    //val bb:ByteBuffer = ByteBuffer.allocate(testStringAsByte.size)

    //bb.put(testStringAsByte)

    //bb
  //}

  //val factory = new EventFactory[BufferEvent] {
    //def newInstance() = BufferEvent(testBuffer())
  //}
//}
//

object CircularByteBuffer {
  def newBuf(mrk:Int) = {
    val cbb = new CircularByteBuffer(mrk)
    cbb.allocate
    cbb
  }
}


/**
 * 1. I think bytebuffer is not helping me here
 */
class CircularByteBuffer(marker:Int, size:Int = bufferBarrier, bufSize:Int = bufferLengthInBytes) {
  //@volatile private var buffer = Array.ofDim[ByteBuffer]( size )
  private var buffer = Array.ofDim[Byte]( size * bufSize)
  private var writePos:Int = 0

  /**
   * Allocates the direct byte buffers
   */
  def allocate = {
    //for(i <- 0 until size) {
      ////buffer(i) = ByteBuffer.allocateDirect(bufSize)
      //buffer(i) = ByteBuffer.allocate(bufSize)
    //}
  }


  /**
   * No synchronization.  Allow for a dirty read and a dirty write this is because
   * we expect only 1 thread to write at a time
   */
  def write(raw:Array[Byte]):Unit = {
    print(marker.toString + ":w: " + writePos)

    //buffer(writePos).put(raw)
    val pos = writePos * bufSize
    java.lang.System.arraycopy(raw, 0, buffer, pos, bufSize)
    writePos = (writePos + 1) % size

    println(" " + Conversions.checksum(raw))
  }

  /**
   * No locks!  Allow for dirty reads
   */
  def read(srcPos:Int):Array[Byte]= {
    val pos = srcPos % size
    //val res = buffer(pos).array()

    val bufPos = pos * bufSize
    val res = java.util.Arrays.copyOfRange(buffer, bufPos, bufPos + bufSize)
    println(marker.toString + ":r: " + pos + " " + Conversions.checksum(res))

    res
  }
}
