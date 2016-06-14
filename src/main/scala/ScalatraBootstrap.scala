import org.scalatra.LifeCycle
import javax.servlet.ServletContext

import org.slf4j.LoggerFactory

import together.audio._

class ScalatraBootstrap extends LifeCycle  {
  private val logger = LoggerFactory.getLogger(getClass)

  override def init(context: ServletContext) {
    AudioServerChannelMult.relay(None)

    context mount (new together.web.LoginServlet, "/auth/*")
    context mount (new together.web.UserServlet, "/user/*")

    logger.debug("Scalatra Init complete")
  }
}
