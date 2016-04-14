package ih

import ih.Conversions._

import java.io._
import java.net.ServerSocket;
import java.net.Socket;

import javax.sound.sampled._
import javax.sound.sampled.SourceDataLine._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

//http://stackoverflow.com/questions/26550514/streaming-audio-from-microphone-with-java
//http://tutorials.jenkov.com/java-networking/udp-datagram-sockets.html
//http://jspeex.sourceforge.net/
// TODO:
// [√] Split Objects, into playback and capture
// [√] Test Server-Client Streaming
// [x] Convert to DataGram (UDP)
// [√] Create 3rd server, for relaying, and sending audio format
// [√] Collect n>2 mics
// [ ] set configs
// [-] mix the sources, run on server
// [ ] Convert futures to threads?
// [ ] add variable quality, resample based on ui
// [ ] Add noise Filtering on capture
// [ ] Add Encryption
// [ ] Add PTT voice breakout
// [ ] Add PTT Room
// [ ] Add speex
// [ ] optimize speed
// [ ] optimize network

object ServerStream {
  def getAudioFormat:AudioFormat = {
    var encoding = AudioFormat.Encoding.PCM_SIGNED;
    //val rate = 44000f
    val rate = 18000f
    val sampleSize = 16
    val bigEndian = true
    val channels = 1

    println("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }


  object RelayServer {
    @volatile var run = true
    @volatile var connId = 0l;
    def getConnId = {
      connId = connId + 1
      connId
    }
    //val r1 = Relay(55555)

    case class Conns(connId:Long, socket:Socket, srcIp:String, streamFctr:Float = 1)

    def connectToServer(serverName:String, port:Int) = {
      println("Connecting to:" + serverName + " ON:" + port)
      //val client = new DatagramSocket(port, InetAddress.getByName(serverName))
      val client = new Socket(serverName,port);

      client
    }

    class Relay(port:Int) {
      var allConns = List[Conns]()

      val serverSocket:ServerSocket = new ServerSocket(port)

      Future(while(run) {
        println("Waiting for a connection on " + port)
        val socket:Socket = serverSocket.accept();

        handleSocket(socket)
      })

      /**
       * I think i need to repace the future with a true thread
       */
      def handleSocket(socket:Socket):Future[Unit] = Future {
        println("Added new socket connection: " + socket.getInetAddress.getHostAddress)
        val newConn =  Conns(getConnId, socket, socket.getInetAddress.getHostAddress)
        allConns = newConn +: allConns

        //val bufSize = 512*5
        val bufSize = 64*1
        var bytesRead = 0

        // pipe input to all connections
        // val in = socket.getInputStream
        // while(true) {
        //   bytesRead = in.read(bytes)
        //   if(allConns.size > 1) {
        //     val others:List[Conns] = allConns.filter(_.connId != newConn.connId)
        //     val playBytes = if(bytesRead == -1) {
        //       System.out.print("-")
        //       Array.ofDim[Byte](bufSize)
        //     } else {
        //       System.out.print(".")
        //       bytes
        //     }

        //     others.foreach { c =>
        //       //System.out.print("("newConn.connId+">"+c")")
        //       val out = c.socket.getOutputStream
        //       out.write(playBytes)
        //     }

        //   } else {
        //     println("no connections. sleeping")
        //     Thread.sleep(1*1000)
        //   }
        // }

        // Pipe output to all connections
        val out = newConn.socket.getOutputStream()
        while(true) {
          if(allConns.size > 1) {
            val others:List[Conns] = allConns.filter(_.connId != newConn.connId)

            // working with shorts //var allStreams:List[Array[Byte]] = others.map { c =>
            // working with shorts var allStreams:List[Array[Short]] = others.map { c =>
            // working with shorts   val bytes = Array.ofDim[Byte](bufSize)
            // working with shorts   val bytesRead = c.socket.getInputStream().read(bytes)
            // working with shorts   if(bytesRead == -1) {
            // working with shorts     //Array.ofDim[Byte](bufSize)
            // working with shorts     Array.ofDim[Short](bufSize / timesShort)
            // working with shorts   } else {
            // working with shorts     //bytes
            // working with shorts     toShortArray(bytes)
            // working with shorts   }
            // working with shorts }

            // working with shorts //out.write(allStreams(0))
            // working with shorts //add the sources and average them out and multiply by the factor
            // working with shorts out.write(ShortToByte_ByteBuffer_Method(allStreams(0)))

            val level = others.size
            val baseline = Array.ofDim[Byte](bufSize)
            var sumStreams:Array[Byte] = others.foldLeft(baseline){ ( l,c ) =>
              val readBytes = Array.ofDim[Byte](bufSize)
              val byteCt= c.socket.getInputStream().read(readBytes)
              if(byteCt == -1) {
                l
              } else {
                for(i <- 0 until bufSize) {
                  l(i) = (l(i) + (((readBytes(i) / level)) * c.streamFctr).toInt).toByte
                }
                l
              }
            }
            out.write(sumStreams)

            // dynamic level check -- donesnt work: val othersGrouped:Map[String, List[Conns]] = allConns.filter(_.connId != newConn.connId).groupBy(_.srcIp)

            // dynamic level check -- donesnt work: val others:Iterable[Array[Byte]] = othersGrouped.values.map { conns =>
            // dynamic level check -- donesnt work:   // find the conn with the highest average dynamic level
            // dynamic level check -- donesnt work:   val baseline = Array.ofDim[Byte](bufSize)
            // dynamic level check -- donesnt work:   var dynamicLevel = 0d;

            // dynamic level check -- donesnt work:   conns.foreach { c =>
            // dynamic level check -- donesnt work:     val readBytes = Array.ofDim[Byte](bufSize)
            // dynamic level check -- donesnt work:     val byteCt= c.socket.getInputStream().read(readBytes)
            // dynamic level check -- donesnt work:     if(byteCt != -1) {
            // dynamic level check -- donesnt work:       applyFctr(readBytes, c.streamFctr)
            // dynamic level check -- donesnt work:       val newDynamicLevel = getLevel(readBytes)
            // dynamic level check -- donesnt work:       if(newDynamicLevel > dynamicLevel) {
            // dynamic level check -- donesnt work:         java.lang.System.arraycopy(readBytes, 0, baseline, 0, bufSize)
            // dynamic level check -- donesnt work:         dynamicLevel = newDynamicLevel
            // dynamic level check -- donesnt work:       }
            // dynamic level check -- donesnt work:     }
            // dynamic level check -- donesnt work:   }

            // dynamic level check -- donesnt work:   baseline
            // dynamic level check -- donesnt work: }

            // dynamic level check -- donesnt work: val level = others.size
            // dynamic level check -- donesnt work: val baseline = Array.ofDim[Byte](bufSize)
            // dynamic level check -- donesnt work: var sumStreams:Array[Byte] = others.foldLeft(baseline){ ( l,r ) =>
            // dynamic level check -- donesnt work:   for(i <- 0 until bufSize) {
            // dynamic level check -- donesnt work:     l(i) = (l(i) + (r(i) / level)).toByte
            // dynamic level check -- donesnt work:   }
            // dynamic level check -- donesnt work:   l
            // dynamic level check -- donesnt work: }

            // dynamic level check -- donesnt work: out.write(sumStreams)

          } else {
            println("no connections. sleeping")
            Thread.sleep(1*1000)
          }
        }
      }
    }

    def applyFctr(ad: Array[Byte], fctr:Float):Unit = {
      var i = 0
      while (i<ad.length) {
        ad(i) = (ad(i) * fctr).toByte
      }
    }

    def getLevel(ad: Array[Byte]):Double = {
      var sumPos = 0d
      var sumNeg = 0d
      var i = 0
      while (i<ad.length) {
        if(ad(i) > 0) {
          sumPos += ad(i)
        } else {
          sumNeg += ad(i)
        }
        i += 1
      }
      sumPos - sumNeg
    }
  }

  object HereServer {
    def getOut(port:Int):OutputStream = {
      val serverSocket:ServerSocket = new ServerSocket(port)
      val server:Socket = serverSocket.accept();
      new DataOutputStream(server.getOutputStream());
    }

    def getIn(serverName:String, port:Int):InputStream = {
      val client = new Socket(serverName,port);
      client.getInputStream();
      //val client = new DatagramSocket(port, InetAddress.getByName(serverName))
      //client.getInputStream();
    }

  }

  class Capture(socket:Option[Socket]) {
    @volatile var halt = false;

    def getOut = {
      //new FileOutputStream("/tmp/test.pcm");
      socket match {
        case Some(s) => s.getOutputStream()
        case _ => HereServer.getOut(5555)
      }
    }

    def runIt:Unit  = {
      var duration = 0;
      var audioInputStream = null;
      var line:TargetDataLine = null;

      // define the required attributes for our line,
      // and make sure a compatible line is supported.

      val format = getAudioFormat;
      val info = new DataLine.Info(classOf[TargetDataLine], format);

      if (!AudioSystem.isLineSupported(info)) {
        println("Line matching " + info + " not supported.");
      }

      // get and open the target data line for capture.
      try {
        println("INFO: " + info);
        line = AudioSystem.getLine(info).asInstanceOf[TargetDataLine];
        line.open(format, line.getBufferSize());
      } catch {
        case ex:Exception=> ex.printStackTrace
      }

      // play back the captured audio data
      val out = getOut
      val frameSizeInBytes = format.getFrameSize();
      //val bufferLengthInFrames = line.getBufferSize() / 8;
      //val bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
      val bufferLengthInBytes = 64
      val data = Array.ofDim[Byte]( bufferLengthInBytes );
      var numBytesRead = 0;

      line.start();

      while (!halt) {
        numBytesRead = line.read(data, 0, bufferLengthInBytes)
        if(numBytesRead == -1) {
          halt = true
        }
        println(Conversions.toShortArray(data).mkString(","))
        out.write(data, 0, numBytesRead);
      }

      // we reached the end of the stream.  stop and close the line.
      line.stop();
      line.close();
      line = null;

      // stop and close the output stream
      try {
        out.flush();
        out.close();
      } catch {
        case ex:IOException => ex.printStackTrace();
      }

    }

    def haltAfter(ms:Long) = {
      Thread.sleep(ms)
      println("halting")
      halt = true
    }
  }

  class Playback(socket:Option[Socket]) {
    @volatile var pwait = true;

    //var bufSize = 16384;
    var bufSize = 512*1
    def getIn = {
      //val file = new File("/tmp/test.pcm")
      //new FileInputStream(file)
      socket match {
        case Some(s) => s.getInputStream()
        case _ => HereServer.getIn("localhost", 5555)
      }

    }

    def runIt = {
      var line:SourceDataLine = null

      val format = getAudioFormat;
      val frameSizeInBytes = format.getFrameSize();

      val fileStream = getIn
      val info = new DataLine.Info(classOf[SourceDataLine], format);

      if (!AudioSystem.isLineSupported(info)) {
        println("Line matching " + info + " not supported.");
      }

      println("INFO: " + info);
      line = AudioSystem.getLine(info).asInstanceOf[SourceDataLine]
      line.open(format, bufSize);

      //val bufferLengthInFrames = line.getBufferSize() / 8;
      //val bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
      val bufferLengthInBytes = 64
      val data = Array.ofDim[Byte]( bufferLengthInBytes );
      var numBytesRead = 0;

      // start the source data line
      line.start();

      var keeprunning = true
      var keeprunning2 = true
      println("Starting Playback")

      val bytes = Array.ofDim[Byte](bufferLengthInBytes)
      while (pwait) {
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
            var numBytesRemaining = numBytesRead;
            while (numBytesRemaining > 0 ) {
              numBytesRemaining -= line.write(data, 0, numBytesRemaining);
            }
          }
        }
      }

      println("Ending Playback")

      // we reached the end of the stream.  let the data play out, then
      // stop and close the line.
      line.drain();
      line.stop();
      line.close();
      line = null;
    }

    def haltAfter(ms:Long) = {
      Thread.sleep(ms)
      println("halting")
      pwait = false
    }
  }


  def capture = {
    val c = new Capture(None);
    val f1 = Future(c.runIt)
    val f2 = Future(c.haltAfter(10*1000))
  }

  def playback = {
    val p = new Playback(None)
    Future(p.runIt)
    Future(p.haltAfter(10*1000))
    //p.halt = true
  }

  def relay(file:Option[String]) = {
    val prop = new java.util.Properties()
    val port:Option[Int] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      port <- Option(prop.getProperty("listen"))
    } yield {
      port.toInt
    }
    val r = new RelayServer.Relay(port.getOrElse(55555))
    readLine()
  }

  def runclient(file:Option[String]) = {
    val prop = new java.util.Properties()
    val hostAndPort:Option[(String, Int)] = for {
      propFileName <- file
      load <- Option(prop.load(new java.io.FileReader(propFileName)))
      host <- Option(prop.getProperty("ihserver.host"))
      port <- Option(prop.getProperty("ihserver.port"))
    } yield {
      (host, port.toInt)
    }

    val host = hostAndPort.map(_._1).getOrElse("localhost")
    val port = hostAndPort.map(_._2).getOrElse(55555)
    val socket = Option(RelayServer.connectToServer(
      host, port
    ))

    val c = new Capture(socket)
    val f1 = Future(c.runIt)

    val p = new Playback(socket)
    Future(p.runIt)

    readLine()

    val f2 = Future(c.haltAfter(10*1000))
    Future(p.haltAfter(10*1000))
  }


  def main (args:Array[String]):Unit = {
    val file = args.length match {
      case 1 => None
      case 2 => Some(args(1))
    }
    println(args(0) + " " + file)
    args(0) match {
      case i if i == "rc" => runclient (file)
      case i if i == "r" => relay (file)
      case i if i == "c" => capture
      case _ => playback
    }
  }
}
