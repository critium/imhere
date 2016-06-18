package together.util

import java.nio._
import together.audio._
import scala.collection.mutable

import org.slf4j.LoggerFactory

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
 * TODO: I think there is a problem with my reader spot.  There is a lot of empty sound before i hear anything
 *       FIX: i think i need to auto-advance the reader position to the next non-zero bufer block
 * TODO: Also, the other problem is that once the writer stops writing, the reader gleefully continues on going
 *       around the buffer and doesnt stop at the barrier
 *       FIX: I thnk i need to implement a writer block
 */
class CircularByteBuffer(marker:Int, size:Int = bufferBarrier, bufSize:Int = bufferLengthInBytes) {
  //@volatile private var buffer = Array.ofDim[ByteBuffer]( size )
  private var buffer = Array.ofDim[Byte]( size * bufSize)
  @volatile private var writePos:Int = 0
  @volatile private var bufPos:Int = 0
  private var readers = mutable.Map[Long,Int]()
  private val logger = LoggerFactory.getLogger("disruptor")

  /**
   * Allocates the direct byte buffers
   */
  def allocate = {
    //for(i <- 0 until size) {
      ////buffer(i) = ByteBuffer.allocateDirect(bufSize)
      //buffer(i) = ByteBuffer.allocate(bufSize)
    //}
  }

  def register(userId:Long):Unit = {
    if(! readers.get(userId).isDefined) {
      val startPos = if(writePos <= 0) {
        0
      } else {
        writePos - 1
      }
      readers += (userId -> startPos)

      logger.debug(":" + marker.toString + ":s: registering at " + startPos)
    }
  }

  /**
   * No synchronization.  Allow for a dirty read and a dirty write this is because
   * we expect only 1 thread to write at a time
   */
  def write(raw:Array[Byte]):Unit = {
    logger.debug(marker.toString + ":w:" + writePos)

    //buffer(writePos).put(raw)
    val pos = bufPos * bufSize

    val minReader = readers.values.foldLeft(java.lang.Integer.MAX_VALUE)( (l:Int,r:Int) => if(l < r) {
      l
    } else {
      r
    })

    //if(readers.size > 0) {
      //print(":m" + marker.toString + ":" + minReader)
      //// this is too agressivly locking
      //while(writePos <= minReader) {
        //print(":wlck:" + marker.toString)
        //Thread.sleep(100)
      //}
    //}

    java.lang.System.arraycopy(raw, 0, buffer, pos, bufSize)
    writePos = writePos + 1
    bufPos = writePos % size

    logger.debug("=>:"+marker.toString + ":" + Conversions.checksum(raw))
  }

  def calcPos = (writePos - bufSize) match {
    case i if i <0 => 0
    case i => i
  }

  /**
   * No locks!  Allow for dirty reads
   */
  def read(posMaybe:Option[Int], userId:Long):(Int, Array[Byte])= {
    //val pos:Int = readers(userId)
    //val res = buffer(pos).array()

    val pos = posMaybe.getOrElse(calcPos)

    logger.debug(marker.toString + ":r:" + pos)

    // not sure this is the best way to do this but here we are
    while(pos >= writePos) {
      logger.debug(">" + marker.toString + ":rlck:")
      Thread.`yield`
      Thread.sleep(50)
    }
    if(marker == 1) logger.debug("<" + marker.toString + ":r: unlocked")

    val bufPos = (pos % size) * bufSize
    val res = java.util.Arrays.copyOfRange(buffer, bufPos, bufPos + bufSize)

    logger.debug(":" + marker.toString + ":" + Conversions.checksum(res))

    readers += (userId -> (pos + 1))

    (pos + 1, res)
  }
}
