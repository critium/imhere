package together.audio

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

object AudioClient {
  private val logger = LoggerFactory.getLogger(getClass)

  def getAudioFormat:AudioFormat = {
    println("FORMAT: enc:" + encoding.toString() + " r:" + rate + " ss:" + sampleSize + " c:" + channels + " be:" + bigEndian);
    return new AudioFormat(encoding, rate, sampleSize, channels, (sampleSize/8)*channels, rate, bigEndian);
  }

  def connectToServer(serverName:String, port:Int) = {
    println("Connecting to:" + serverName + " ON:" + port)
    //val client = new DatagramSocket(port, InetAddress.getByName(serverName))
    val client = new Socket(serverName,port);

    client.setTcpNoDelay(true)

    client
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

      val sbuffer:SoftAudioBuffer  = new SoftAudioBuffer(bufferLengthInBytes / Conversions.timesShort, format)
      val filter:SoftFilter = new SoftFilter(format.getSampleRate())
      filter.setFilterType(SoftFilter.FILTERTYPE_BP12);
      filter.setResonance(voiceResonance);
      filter.setFrequency(voiceFrequency);
      val data = Array.ofDim[Byte]( bufferLengthInBytes )
      var numBytesRead = 0;

      val converter = AudioFloatConverter.getConverter(format)

      line.start();

      // we use short bc we have encoded in 16 bit
      // switched to float so we can use the bandpass
      val floatBuf:Array[Float] = Array.ofDim[Float](bufferLengthInBytes / timesShort)

      val pauseCtr = 100
      @volatile var ctr = 0
      while (!halt) {
        //print("out:")
        numBytesRead = line.read(data, 0, bufferLengthInBytes)
        if(numBytesRead == -1) {
          halt = true
        }

        //val signal:Array[Short] = Conversions.toShortArray(data)
        val signal:Array[Float] = converter.toFloatArray(data, 0, floatBuf, 0, floatBuf.length)
        val sbufferData:Array[Float] = sbuffer.array()
        java.lang.System.arraycopy(signal, 0, sbufferData, 0, sbufferData.length)
        //println("Orig: " + signal.mkString(","))

        filter.processAudio(sbuffer)

        //println("Filt:" + sbufferData.mkString(","))

        sbuffer.get(data, 0)

        println("Sending: " + Conversions.checksum(data))

        out.write(data, 0, numBytesRead);
        out.flush()

        if(pauseCtr < ctr) {
          Thread.sleep(60000)
        } else {
          ctr = ctr + 1
        }
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
      //val frameSizeInBytes = format.getFrameSize();

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

  class WebClient(host:String, uid:Long, name:String, domainId:Long, groupId:Long) {
    import scalaj.http._
    import together.data._
    import org.json4s._
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._
    import org.json4s.jackson.Serialization
    import org.json4s.jackson.Serialization.{read, write}

    var protocol = "http://"
    var auth = "/auth"
    var loginP = s"${auth}/login"

    implicit val formats = DefaultFormats

    var loginInfo:Option[LoginInfo] = None

    def login:String = {
      val url = s"${protocol}${host}${loginP}"
      val user:User = User(uid, name, domainId, groupId, "#")
      val json:JValue = user
      val data:String = write(json)
      val response: HttpResponse[String] =
        Http(url).postData(data).header("content-type", "application/json").asString

      loginInfo = Try(parse(response.body).extract[LoginInfo]) match {
        case Success(v) => Some(v)
        case Failure(e) =>
          e.printStackTrace()
          None
      }

      println("  RCV: " + loginInfo)

      if(loginInfo.isDefined) {
        "web logged in"
      } else {
        "not logged in"
      }
    }
  }

  class ConsoleClient {
    @volatile var running = true

    val WEBCONNECT = "webconnect"

    var wc:Option[WebClient] = None

    var consoleMsg = """
    Commands are (yes, it is case sensitive):
      halt                                            - stop this server
      webconnect <hostname> <uid> <uname> <did> <gid> - connect to web server
      audioconnect                                    - connect to audio server
      disconnect                                      - disconnect to both audio and webserver
    """

    var prompt = "=> "

    def run = {
      println(consoleMsg)
      while(running) {
        val command = readLine()

        command match {
          case h if h.equals("halt") =>
            running = false
          case h if h.startsWith(WEBCONNECT) =>
            wc match {
              case Some(wc) =>
                println("Already connected. Disconnect first")
              case _ =>
                val cmd:Array[String] = h.split(" ")
                cmd.size match {
                  case i if i == 2 =>
                    val host = "localhost:8080"
                    val uid = cmd(1).toLong
                    val name = s"test${uid}"
                    val domainId = 1l
                    val groupId = 1l
                    println(s"Connecting to ${host} as ${uid}/${name}/${domainId}/${groupId}")
                    wc = Some(new WebClient(host, uid, name, domainId, groupId))
                    wc.map(_.login)
                  case i if i == 6 =>
                    val host = cmd(1)
                    val uid = cmd(2).toLong
                    val name = cmd(3)
                    val domainId = cmd(4).toLong
                    val groupId = cmd(5).toLong
                    println(s"Connecting to ${host} as ${uid}/${name}/${domainId}/${groupId}")
                    wc = Some(new WebClient(host, uid, name, domainId, groupId))
                    wc.map(_.login)
                  case _ =>
                    println("->Unable to connect.  Missing parameters")
                }
            }
          case h if h.equals("audioconnect") =>
            val res = for {
              wc <- wc
              li <- wc.loginInfo
            } yield {
              val host = li.hostInfo.name
              val port = li.hostInfo.port
              runclient(host, port, Some(li))
              println(s"Connecting to server....${host}:${port}")
              Unit
            }

            if(!res.isDefined) {
              println(s"No Login info to connect to server.")
            }
          case h if h.equals("disconnect") =>
            println("not yet implemented")
          case _ =>
            println(consoleMsg)
        }

        print(prompt)
      }
    }
  }

  def handshake(loginInfo:LoginInfo, socket:Socket) = {
    val out = socket.getOutputStream()
    val in = socket.getInputStream()
    AudioLogin.toStream(loginInfo.toAudioLogin, out)
    out.flush()

    // wait for ACK
    println("Awaiting ACK...")
    val ack = AudioAck.fromStream(in)
    println("ACK: " + ack.toString)
  }

  def runclient(host:String, port:Int, loginInfo:Option[LoginInfo]):Unit = {
    val socket = Option(connectToServer(
      host, port
    ))

    // perform the handshake if given login info.  Otherwise, send away!
    if(loginInfo.isDefined && socket.isDefined) {
      handshake(loginInfo.get, socket.get)
    }

    val c = new Capture(socket)
    val f1 = Future(c.runIt)

    val p = new Playback(socket)
    Future(p.runIt)

    readLine()

    val f2 = Future(c.haltAfter(10*1000))
    Future(p.haltAfter(10*1000))
  }

  def runclient(file:Option[String]):Unit = {
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

    runclient(host, port, None)
  }


  def main (args:Array[String]):Unit = {
    new ConsoleClient().run
  }
}
