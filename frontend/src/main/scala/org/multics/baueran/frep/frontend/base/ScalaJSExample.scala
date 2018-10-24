package org.multics.baueran.frep.frontend.base

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw._

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.annotation._
import org.querki.jquery._

import scalatags.JsDom.all._

@JSExportTopLevel("ScalaJsExample")
object ScalaJSExample {
  val hspace = div(cls:="col-xs-12", style:="height:50px;")

  def main(args: Array[String]): Unit = {
    $("#nav_bar").empty()
    $("#nav_bar").append(hspace.apply().render)
//    $("#nav_bar").append(NavBar.apply().render)
    $("#content").append(Repertorise.apply().render)
  }
}
