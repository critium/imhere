package together.data

import together.audio._
import com.typesafe.config._
import org.slf4j.LoggerFactory
import scala.util._

object ServiceLocator {
  private val logger = LoggerFactory.getLogger(getClass)
  private val config = ConfigFactory.load()

  val dataService = new DataServiceImpl
  val channelService = getEnv() match {
    case i if i.equalsIgnoreCase("test") =>
      logger.debug("Creating a test channel")
      new ChannelServiceImpl(true)
    case _ =>
      logger.debug("Creating a threaded channel")
      new ChannelServiceImpl(false)
  }
  val orchestrationService = new OrchestrationServiceImpl(channelService, dataService)

  def getEnv():String = {
    val res = Try(config.getString("application.env")) match {
      case Success(v) =>
        v
      case Failure(v) => {
        ""
      }
    }

    logger.debug("ENV: " + res)

    res
  }

}
