package together

import org.specs2._
import org.specs2.specification.Scope
import java.io._
import java.net._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import together.data._
import together.audio.Conversions._


class AudioLoginSpec extends mutable.Specification {
  "test audio user stream conversion" in {
    val out = new ByteArrayOutputStream();
    val al1 = AudioLogin(1,"#")
    AudioLogin.toStream(al1, out)
    val in = new ByteArrayInputStream(out.toByteArray)
    val al2 = AudioLogin.fromStream(in).get

    al1.userId must equalTo(al2.userId)
    al1.hash must equalTo(al2.hash)
  }

  //"test socket" in new sockets[AudioUser] {
    //testSocket((socket) => {
      //AudioUser.fromStream(socket.getInputStream).get
    //})
    //true must equalTo(true)
  //}
}

trait sockets[T] extends Scope {
  val testPort = 55555
  val serverSocket:ServerSocket = new ServerSocket()
  serverSocket.setReuseAddress(true)
  serverSocket.bind(new InetSocketAddress(testPort))

  def testSocket(f:(Socket) => T):Future[T] = Future {
    val theSocket = serverSocket.accept()
    val res = f(theSocket)

    theSocket.close()
    serverSocket.close()

    res
  }
}
