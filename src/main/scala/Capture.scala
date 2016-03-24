package ih

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
// [x] Split Objects, into playback and capture
// [x] Test Server-Client Streaming
// [p] Convert to DataGram (UDP)
// [ ] Create 3rd server, for relaying, and sending audio format
// [ ] Collect n mics
// [ ] Add speex
// [ ] add variable quality, resample based on ui
// [ ] Add Encryption

object ServerStream {
  def getAudioFormat:AudioFormat = {
    var encoding = AudioFormat.Encoding.PCM_SIGNED;
    val rate = 44000f
    val sampleSize = 16
    val bigEndian = true
    val channels = 2

    println("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }


  object RelayServer {
    val r1 = Relay(55555)

    case class Conns(socket:Socket)

    class Relay(port) {
      var allConns = List[Conns]()

      val serverSocket:ServerSocket = new ServerSocket(port)

      while(runIt) {
        println("Waiting for a connection")
        val server:Socket = serverSocket.accept();
      }

      def handleSocket(socket:Socket):Future[Unit] = Future {
        allConns += Conns(socket)

        // pipe input to all connections
        val out = socket.getOutputStream
        val bufSize = 1024*5
        val bytes = Array.ofDim[Byte](bufSize)
        while(true) {
          if(allConns.size > 1) {
            val others:List[Conn] = allConns.filter(_.socket != socket)
            val playBytes = if(out.available == 0 ) {
              Array.ofDim[Byte](bufSize)
            } else {
              bytes
            }
            others.foreach { c =>
              c.socket.getInputStream.write(playBytes)
            }
          } else {
            Thread.sleep(1*1000)
          }
        }
      }

      def setSender(port:Int, host:InetAddress) = {
        sender = Conns(port, host)
      }

      def setReceiver(port:Int, host:InetAddress) = {
        receiver = Conns(port, host)
      }
    }
  }

  object HereServer {
    def getOut(port:Int):OutputStream = {
      val serverSocket:ServerSocket = new ServerSocket(port)
      val server:Socket = serverSocket.accept();
      new DataOutputStream(server.getOutputStream());
    }

    def getIn(serverName:String, port:Int):InputStream = {
      //val client = new Socket(serverName,port);
      //client.getInputStream();

      val client = new DatagramSocket(port, InetAddress.getByName(serverName))
      client.getInputStream();

    }

  }

  class Capture {
    @volatile var halt = false;

    def getOut = {
      //new FileOutputStream("/tmp/test.pcm");
      HereServer.getOut(5555)
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
      val bufferLengthInFrames = line.getBufferSize() / 8;
      val bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
      val data = Array.ofDim[Byte]( bufferLengthInBytes );
      var numBytesRead = 0;

      line.start();

      while (!halt) {
        numBytesRead = line.read(data, 0, bufferLengthInBytes)
        if(numBytesRead == -1) {
          halt = true
        }
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

  class Playback {
    @volatile var pwait = true;

    var bufSize = 16384;
    def getIn = {
      //val file = new File("/tmp/test.pcm")
      //new FileInputStream(file)
      HereServer.getIn("localhost", 5555)
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

      val bufferLengthInFrames = line.getBufferSize() / 8;
      val bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
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
    val c = new Capture();
    val f1 = Future(c.runIt)
    val f2 = Future(c.haltAfter(10*1000))
  }

  def playback = {
    val p = new Playback()
    Future(p.runIt)
    Future(p.haltAfter(10*1000))
    //p.halt = true
  }


  def main (args:Array[String]):Unit = {
    println(args(0))
    args(0) match {
      case i if i == "c" => capture
      case _ => playback
    }
  }
}
