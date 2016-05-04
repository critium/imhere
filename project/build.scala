//import com.earldouglas.xsbtwebplugin.PluginKeys._
//import com.earldouglas.xsbtwebplugin.WebPlugin._
//import com.mojolly.scalate.ScalatePlugin.ScalateKeys._
//import com.mojolly.scalate.ScalatePlugin._
//import org.scalatra.sbt._
//import sbt.Keys._
//import sbt._

import sbt._
import Keys._
import org.scalatra.sbt._
import org.scalatra.sbt.PluginKeys._
import com.earldouglas.xwp.JettyPlugin
import com.mojolly.scalate.ScalatePlugin._
import ScalateKeys._



object imhereproject extends Build {
  val Organization = "together"
  val Name = "together-prototype"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.8"
  //val ScalatraVersion = "2.4.0"
  //val Json4sVersion = "3.2.11"
  //val Json4sVersion = "3.3.0.RC1"
  val ScalatraVersion = "2.3.0"
  val Json4sVersion = "3.2.9"

  lazy val project = Project (
    "ih",
    file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      fork in run := true,
      connectInput in run := true,

      // Read here for optional jars and dependencies
      libraryDependencies ++= Seq(
        "org.json4s"        %% "json4s-jackson"    % Json4sVersion,

        "org.scalatra"      %% "scalatra"          % ScalatraVersion,
        "org.scalatra"      %% "scalatra-scalate"  % ScalatraVersion,
        "org.scalatra"      %% "scalatra-json"     % ScalatraVersion,
        "org.scalatra"      %% "scalatra-specs2"   % ScalatraVersion    % "test",

        "org.specs2"        %% "specs2-core"       % "3.7.2"            % "test" force(),
        "ch.qos.logback"    %  "logback-classic"   % "1.1.3"            % "runtime",
        "org.eclipse.jetty" %  "jetty-webapp"      % "9.2.10.v20150310" % "container;compile",
        "javax.servlet"     %  "javax.servlet-api" % "3.1.0"            % "provided",
        "org.scalaj"        %% "scalaj-http"       % "2.3.0",
        "com.lmax"          %  "disruptor"         % "3.3.4"

      ),

      scalacOptions in Test ++= Seq("-Yrangepos")

      )
  ).enablePlugins(JettyPlugin)
}





