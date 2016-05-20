import org.scalatra.LifeCycle
import javax.servlet.ServletContext

import org.slf4j.LoggerFactory

import together.audio._
import together.audio.AudioServer._

class ScalatraBootstrap extends LifeCycle  {
  private val logger = LoggerFactory.getLogger(getClass)

  override def init(context: ServletContext) {
    // Mount our servlets as normal:
    System.getenv("VERSION") match {
      case i if i.equals("channel") => AudioServerChannelMult.relay(None)
      case i if i.equals("mult") => AudioServerMult.relay(None)
      case _ => AudioServer.relay(None)
    }

    context mount (new together.web.LoginServlet, "/auth/*")
    context mount (new together.web.UserServlet, "/user/*")

    logger.debug("Scalatra Init complete")
  }
}
