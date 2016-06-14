package together.web

import org.scalatra._
import org.scalatra.servlet.SizeConstraintExceededException

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import org.json4s.{DefaultFormats, Formats}

import org.scalatra.json.JacksonJsonSupport

import org.slf4j.LoggerFactory

import together.data._

class UserServlet extends ScalatraServlet with JacksonJsonSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  val dataService = DataService.default

  protected implicit val jsonFormats: Formats = DefaultFormats

  put("/:uid/room/:roomId") {
    contentType = formats("json")
    val uid = params("uid").toLong
    val roomId = params("roomId").toLong

    dataService.moveToRoom(uid, roomId)
  }

  get("/:uid/rooms") {
    contentType = formats("json")
    val uid = params("uid").toLong

    val rooms:List[Room] = dataService.listRooms(uid)
    val res:JValue = rooms

    render(("rooms" -> res))
  }

}
