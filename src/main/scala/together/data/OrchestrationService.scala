package together.data

import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel

import together.util._
import together.audio._

import scala.collection.mutable.{Map => MutableMap}
import scala.util._

import org.slf4j.LoggerFactory

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

trait OrchestrationService {
  def logout(userId:Long):Boolean
}

class OrchestrationServiceImpl(cs:ChannelServiceTrait, ds:DataServiceTrait) extends OrchestrationService {
  override def logout(userId:Long):Boolean = {
    ds.logout(userId) && cs.logoutAudio(userId)
  }
}
