import org.scalatra.LifeCycle
import javax.servlet.ServletContext

import org.slf4j.LoggerFactory

class ScalatraBootstrap extends LifeCycle  {
  private val logger = LoggerFactory.getLogger(getClass)

  override def init(context: ServletContext) {
    // Mount our servlets as normal:
    context mount (new together.web.LoginServlet, "/auth/*")
  }
}
