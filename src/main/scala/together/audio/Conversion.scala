package together.audio

import java.lang.Math
import java.nio._

/**
 * Parts converted from http://stackoverflow.com/questions/16389205/simple-bandpass-filter-in-java
 */
object Conversions {
  val dByteLen = 8
  def toByteArray(value:Double):Array[Byte] = {
    val bytes = Array.ofDim[Byte](dByteLen)
    ByteBuffer.wrap(bytes).putDouble(value)
    bytes
  }

  val sByteLen = 2
  def toByteArrayFromShort(value:Short):Array[Byte] = {
    val bytes = Array.ofDim[Byte](sByteLen)
    ByteBuffer.wrap(bytes).putShort(value)
    bytes
  }

  def toByteArrayFromShortArray(value:Array[Short]):Array[Byte] = {
    val bytes = Array.ofDim[Byte](sByteLen * value.length)
    value.foreach { v =>
      ByteBuffer.wrap(bytes).putShort(v)
    }
    bytes
  }

  def ShortToByte_ByteBuffer_Method(input:Array[Short]):Array[Byte] = {
    var iterations = input.length;

    var bb = ByteBuffer.allocate(input.length * 2);

    for(index <- 0 until iterations) {
      bb.putShort(input(index));
    }

    bb.array();
  }

  def toDouble(bytes:Array[Byte]):Double = {
    ByteBuffer.wrap(bytes).getDouble()
  }

  //def toDoubleArray(bytes:Array[Byte]):Array[Double] = {
    //val db = ByteBuffer.wrap(bytes).asDoubleBuffer()
    //db.hasArray()
    //db.array()
  //}

  val timesDouble = java.lang.Double.SIZE / java.lang.Byte.SIZE
  def toDoubleArray(byteArray:Array[Byte]):Array[Double] = {
    val doubles = Array.ofDim[Double]( byteArray.length / timesDouble )
    for(i <- 0 until doubles.length) {
      val byteBuff = ByteBuffer.wrap(byteArray, i*timesDouble, timesDouble)
      byteBuff.order(ByteOrder.BIG_ENDIAN);
      doubles(i) = byteBuff.getDouble();
    }
    doubles
  }

  val timesShort = java.lang.Short.SIZE / java.lang.Byte.SIZE
  def toShortArray(byteArray:Array[Byte]):Array[Short] = {
    val shorts = Array.ofDim[Short]( byteArray.length / timesShort )
    for(i <- 0 until shorts.length) {
      val byteBuff = ByteBuffer.wrap(byteArray, i*timesShort, timesShort)
      byteBuff.order(ByteOrder.BIG_ENDIAN);
      shorts(i) = byteBuff.getShort();
    }
    shorts
  }

  def toDoubleArray(shortArray:Array[Short]):Array[Double] = {
    val doubles = Array.ofDim[Double](shortArray.length)
    for(i <- 0 until doubles.length) {
      doubles(i) = shortArray(i).asInstanceOf[Double]
    }
    doubles
  }

  /**
   * @see - http://www.mathworks.com/help/signal/ref/blackman.html
   * @param length
   * @return
   */
  def blackmanWindow(length:Int):Array[Double] = {
    val window = Array.ofDim[Double](length)
    val factor = Math.PI / (length - 1);

    for (i <- 0 to window.length) {
      window(i) = 0.42d - (0.5d * Math.cos(2 * factor * i)) + (0.08d * Math.cos(4 * factor * i));
    }

    window
  }

  def lowPassKernel(length:Int, cutoffFreq:Double, window:Array[Double]):Array[Double] = {
    val ker = Array.ofDim[Double](length + 1)
    val factor = Math.PI * cutoffFreq * 2;

    var sum = 0d;
    var d = 0d;

    for (i <- 0 until ker.length) {
      d = i - length/2;
      ker(i) = d match {
        case f if f == 0 => factor
        case _ => Math.sin(factor * d) / d;
      }
      ker(i) = ker(i) * window(i);
      sum = sum + ker(i);
    }

    // Normalize the kernel
    for (i <- 0 until ker.length) {
      ker(i) = ker(9) / sum;
    }

    ker
  }

  def bandPassKernel(length:Int, lowFreq:Double, highFreq:Double):Array[Double] = {

    val ker = Array.ofDim[Double](length + 1)
    val window = blackmanWindow(length + 1)

    // Create a band reject filter kernel using a high pass and a low pass filter kernel
    val lowPass = lowPassKernel(length, lowFreq, window);

    // Create a high pass kernel for the high frequency
    // by inverting a low pass kernel
    val highPass = lowPassKernel(length, highFreq, window);
    for (i <- 0 until highPass.length) {
      highPass(i) = -1 * highPass(i)
    }
    highPass(length / 2) += highPass(length / 2) + 1;

    // Combine the filters and invert to create a bandpass filter kernel
    for (i <- 0 until ker.length) {
      ker(i) = -1 * (lowPass(i) + highPass(i));
    }
    ker(length / 2) = ker(length / 2) + 1;

    return ker;
  }

  def filter(signal:Array[Double], kernel:Array[Double]):Array[Double] = {
    val res= Array.ofDim[Double](signal.length)

    for (r <- 0 until res.length) {
      val min = Math.min(kernel.length, r + 1);
      for (k <- 0 until min) {
        res(r) = res(r) + kernel(k) * signal(r - k);
      }
    }

    res
  }
}
