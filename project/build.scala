import com.earldouglas.xsbtwebplugin.PluginKeys._
import com.earldouglas.xsbtwebplugin.WebPlugin._
import com.mojolly.scalate.ScalatePlugin.ScalateKeys._
import com.mojolly.scalate.ScalatePlugin._
import org.scalatra.sbt._
import sbt.Keys._
import sbt._

object imhereproject extends Build {

  lazy val project = Project (
    "ih",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      fork in run := true,
      connectInput in run := true,

      // Read here for optional jars and dependencies
      libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.7.2" % "test"),
      libraryDependencies ++= Seq("org.json4s" %% "json4s-native" % "3.2.11"),


      scalacOptions in Test ++= Seq("-Yrangepos")

      )
  )
}

