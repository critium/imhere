package together.audio

import java.lang.Math
import java.nio._

object Conversions {
  private val timesInt = java.lang.Integer.SIZE / java.lang.Byte.SIZE
  val timesInt16 = timesInt * 2

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

  def toByteArray(input:Array[Float]):Array[Byte] = {
    var iterations = input.length;
    var bb = ByteBuffer.allocate(input.length * timesFloat);
    for(index <- 0 until iterations) {
      bb.putFloat(input(index));
    }
    bb.array();
  }

  def toDouble(bytes:Array[Byte]):Double = {
    ByteBuffer.wrap(bytes).getDouble()
  }

  val zeroByte:Byte = 0x00000000
  def checksum(data:Array[Byte]):String= {
    printBinary(
      data.foldLeft(zeroByte){ (l:Byte,r:Byte) => (l ^ r).toByte }
    )
  }

  def printBinary(data:Byte):String = {
    javax.xml.bind.DatatypeConverter.printHexBinary(Array(data))
  }

  //def printBinary(data:Array[Byte]):String = {
    //javax.xml.bind.DatatypeConverter.printHexBinary(data)
  //}


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

  def timesFloat = java.lang.Float.SIZE / java.lang.Byte.SIZE
  def toFloatArray(byteArray:Array[Byte]):Array[Float] = {
    val floats = Array.ofDim[Float](byteArray.length / timesFloat)
    for(i <- 0 until floats.length) {
      val byteBuff = ByteBuffer.wrap(byteArray, i*timesFloat, timesFloat)
      byteBuff.order(ByteOrder.BIG_ENDIAN);
      floats(i) = byteBuff.getFloat()
    }
    floats
  }

  def pcmToFloatArray(pcms:Array[Short]):Array[Float] = {
    val floats = Array.ofDim[Float](pcms.length)
    for(i <- 0 until floats.length) {
      floats(i) = pcms(i)
    }
    floats
  }

  def pcmToShortArray(bytes:Array[Byte]):Array[Short] = {
    val shorts = Array.ofDim[Short](bytes.length / 2)
    val bb = ByteBuffer.wrap(bytes);
    for (i <- 0 until shorts.length) {
      shorts(i) = bb.getShort()
    }
    shorts
  }

  def pcmToInt16(stream:Array[Byte]):Array[Int] = {
    val intStream = Array.ofDim[Int](stream.length/2)
    for(i <- 0 until intStream.length ) {
      val pos = i*2;
      intStream(i) = pcmToInt16(stream, pos, bigEndian)
    }

    intStream
  }

  def pcmToInt16(buffer:Array[Byte], byteOffset:Int, bigEndian:Boolean):Int = {
    bigEndian match {
      case true => ((buffer(byteOffset)<<8) | (buffer(byteOffset+1) & 0xFF))
      case _ => ((buffer(byteOffset+1)<<8) | (buffer(byteOffset) & 0xFF))
    }
  }

  def int16ToPCM(intStream:Array[Int]):Array[Byte] = {
    // int 16 to pcm is double length
    val outStream = Array.ofDim[Byte](intStream.length * 2)
    for(i <- 0 until intStream.length ) {
      val pos = i*2;
      int16ToPCM(intStream(i), outStream, pos, bigEndian)
    }

    outStream
  }

  def int16ToPCM(sampleRaw:Int, buffer:Array[Byte], byteOffset:Int, bigEndian:Boolean):Unit = {
    val sample = clipInt16(sampleRaw)
    //val buffer = Array[Byte](16)
    //val buffer = Array.ofDim[Byte](2)

    if (bigEndian) {
      buffer(byteOffset)   = (sample >> 8).toByte
      buffer(byteOffset+1) = (sample & 0xFF).toByte
    }
    else {
      buffer(byteOffset)   = (sample & 0xFF).toByte
      buffer(byteOffset+1) = (sample >> 8).toByte
    }

    //buffer
  }

  def clipInt16(src:Int):Int = {
    if(src > 0 && src > CLIP_PCM) CLIP_PCM
    else if(src < 0 && src < (-1 * CLIP_PCM)) -1 * CLIP_PCM
    else src
  }

  def calculateGain(linearRatio:Float, minDB:Int = DB_MIN, maxDB:Int = DB_MAX):Float = {
    Math.pow(10f,((linearRatio * (maxDB-minDB) + minDB)/20f))
  }.toFloat

  def sumFloatArrays(lF:Array[Float], rF:Array[Float], rFctr:Float):Array[Float] = {
    val floats = Array.ofDim[Float](lF.length)
    for(i <- 0 until lF.size) {
      floats(i) = lF(i) + (rF(i) * rFctr)
      //println(s"FLOAT SUM: ${floats(i)} = ${lF(i)} + (${rF(i)} * $rFctr) ")
    }
    floats
  }

  def normalizeFloat(floats:Array[Float], factor:Float):Array[Float] = {
    for(i <- 0 until floats.size) {
      floats(i) = floats(i)  * factor
    }

    floats
  }

  def sumIntArrays(lF:Array[Int], rF:Array[Int], rFctr:Float):Array[Int] = {
    val ints = Array.ofDim[Int](lF.length)
    for(i <- 0 until lF.size) {
      ints(i) = lF(i) + (rF(i) * rFctr).toInt
      //println(s"FLOAT SUM: ${ints(i)} = ${lF(i)} + (${rF(i)} * $rFctr) ")
    }
    ints
  }

  def normalizeInt(ints:Array[Int], factor:Float):Array[Int] = {
    for(i <- 0 until ints.size) {
      ints(i) = (ints(i)  * factor).toInt
    }

    ints
  }

  def sumFloatBytes(lArr:Array[Byte], rArr:Array[Byte], rFctr:Float):Array[Byte] = {
    val lF = toFloatArray(lArr)
    val rF = toFloatArray(rArr)
    val tF = Array.ofDim[Float](lF.size)

    for(i <- 0 until lF.size) {
      tF(i) = lF(i) + (rF(i) * rFctr)
    }

    toByteArray(tF)
  }

  def toFloatArrayFill(totalLength:Int, fill:Float):Array[Byte] = {
    val floatBuf = ByteBuffer.allocate(totalLength)

    while(floatBuf.hasRemaining) {
      floatBuf.putFloat(fill)
    }

    val floats = floatBuf.array()

    //println("FLOATS FILL: " + fill + " >>>> " + toFloatArray(floats).mkString(","))
    //println(s"Conversion: ${totalLength} / ${timesFloat} / ${numFloats} / ${floats.length}")

    floats
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
