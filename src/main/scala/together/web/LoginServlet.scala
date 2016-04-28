package together.web

import org.scalatra._
import org.scalatra.servlet.SizeConstraintExceededException

//import org.json4s._
//import org.json4s.native.JsonMethods._
//import org.json4s.native.Serialization
//import org.json4s.native.Serialization.{read, write}
//import org.scalatra.json._
//
//import org.json4s.JsonDSL._
import org.json4s.{DefaultFormats, Formats}

import org.scalatra.json.JacksonJsonSupport

import org.slf4j.LoggerFactory

import together.data._

class LoginServlet extends ScalatraServlet with JacksonJsonSupport {
  private val logger = LoggerFactory.getLogger(getClass)

  protected implicit val jsonFormats: Formats = DefaultFormats

  post("/login/?") {
    contentType = formats("json")
    val u:User = parsedBody.extract[User]
    DataService.login(u) match {
      case Some(l) =>
        logger.debug("Sending " + l)
        render(l)
      case _ => BadRequest()
    }


  }

}
