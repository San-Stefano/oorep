package org.multics.baueran.frep.shared.sec_frontend

import fr.hmil.roshttp.HttpRequest
import fr.hmil.roshttp.body.{MultiPartBody, PlainTextBody}
import fr.hmil.roshttp.response.SimpleHttpResponse
import io.circe.parser.parse
import monix.execution.Scheduler.Implicits.global
import org.multics.baueran.frep.shared.Defs.serverUrl
import org.multics.baueran.frep.shared.frontend.Repertorise
import org.multics.baueran.frep.shared.{CaseRubric, Caze, FIle}
import org.multics.baueran.frep.shared.frontend.{Case, Repertorise, getCookieData}
import org.scalajs.dom
import org.scalajs.dom.Event
import scalatags.JsDom.all.{onclick, _}
import rx.Var
import rx.Rx
import rx.Ctx.Owner.Unsafe._
import scalatags.rx.all._
import org.querki.jquery.$

import scalajs.js
import scala.math.{max, min}
import scala.util.{Failure, Success, Try}

object EditFileModal {

  private var currentlyActiveMemberId = -1
  private var currentlyOpenedFile: Option[FIle] = None
  private val currentlySelectedCaseId = Var(-1)
  private val currentlySelectedCaseHeader = Var("")
  private val cases: Var[List[Caze]] = Var(List())
  private val casesHeight = Rx(max(200, min(100, cases().length * 30)))
  private val caseAnchors = Rx {
    cases() match {
      case Nil =>
        List(a(cls:="list-group-item list-group-item-action", data.toggle:="list", id:="-1", href:="#list-profile", role:="tab", "<no cases created yet>").render)
      case _ => cases().map { c =>
        a(cls:="list-group-item list-group-item-action", id:=c.id.toString(), data.toggle:="list", href:="#list-profile", role:="tab",
          onclick:={ (event: Event) =>
            $("#openFileEditFileModal").removeAttr("disabled")
            $("#deleteFileEditFileModal").removeAttr("disabled")
            currentlySelectedCaseId() = c.id
          },
          c.header)
          .render
      }
    }
  }
  val fileName = Var("")

  fileName.foreach { fileHeader =>

    // Update modal dialog with data obtained from backend...
    def updateModal(response: Try[SimpleHttpResponse]) = {
      response match {
        case response: Success[SimpleHttpResponse] => {
          parse(response.get.body) match {
            case Right(json) => {
              val cursor = json.hcursor
              cursor.as[FIle] match {
                case Right(f) =>
                  $("#fileDescrEditFileModal").`val`(f.description) // Update description
                  cases() = f.cazes // Update cases
                  currentlyOpenedFile = Some(f) // Update currently opened file
                case Left(err) => println("Decoding of file failed: " + err)
              }
            }
            case Left(err) => println("Parsing of file failed (is it JSON?): " + err)
          }
        }
        case error: Failure[SimpleHttpResponse] => println("ERROR: " + error.get.body)
      }
    }

    // Reset UI and some data
    cases() = List.empty
    currentlySelectedCaseId() = -1
    currentlySelectedCaseHeader() = ""
    currentlyActiveMemberId = -1
    $("#openFileEditFileModal").attr("disabled", true)
    $("#deleteFileEditFileModal").attr("disabled", true)

    // Request data from backend...
    getCookieData(dom.document.cookie, "oorep_member_id") match {
      case Some(memberId) =>
        if (fileHeader.length() > 0 && memberId.toInt >= 0) {
          currentlyActiveMemberId = memberId.toInt
          HttpRequest(serverUrl() + "/file")
            .withQueryParameters("memberId" -> memberId, "fileId" -> fileHeader)
            .withCrossDomainCookies(true)
            .send()
            .onComplete((r: Try[SimpleHttpResponse]) => updateModal(r))
        }
      case None => println("WARNING: getCasesForFile() failed. Could not get memberID from cookie."); -1
    }
  }

  private def areYouSureModalCase() = {
    div(cls:="modal fade", tabindex:="-1", role:="dialog", id:="editFileModalAreYouSureCase",
      div(cls:="modal-dialog", role:="document",
        div(cls:="modal-content",
          div(cls:="modal-header",
            h5(cls:="modal-title", Rx("Really delete case " + currentlySelectedCaseHeader() + "?")),
            button(`type`:="button", cls:="close", data.dismiss:="modal", aria.label:="Close", span(aria.hidden:="true", "\u00d7"))
          ),
          div(cls:="modal-body",
            p("Deleting this case cannot be undone!")
          ),
          div(cls:="modal-footer",
            button(`type`:="button", cls:="btn btn-secondary", data.dismiss:="modal", "Cancel"),
            button(`type`:="button", cls:="btn btn-primary", data.dismiss:="modal",
              onclick:= { (event: Event) =>
                HttpRequest(serverUrl() + "/delcase")
                  .post(MultiPartBody(
                    "caseId"     -> PlainTextBody(currentlySelectedCaseId.now.toString()),
                    "caseHeader" -> PlainTextBody(currentlySelectedCaseHeader.now),
                    "memberId"   -> PlainTextBody(currentlyActiveMemberId.toString())))
              },
              "Delete")
          )
        )
      )
    )
  }

  private def mainModal() = {

    def getCaseFromCurrentSelection() = {
      val anchorNode = dom.document.querySelector("#editFileAvailableCasesList .active")
      currentlySelectedCaseId() = anchorNode.id.toInt
      currentlySelectedCaseHeader() = anchorNode.textContent
    }

    div(cls:="modal fade", tabindex:="-1", role:="dialog", id:="editFileModal",
      div(cls:="modal-dialog modal-dialog-centered", role:="document", style:="min-width: 80%;",
        div(cls:="modal-content",
          div(cls:="modal-header",
            h5(cls:="modal-title", Rx(fileName())),
            button(`type`:="button", cls:="close", data.dismiss:="modal", "\u00d7")
          ),
          div(cls:="modal-body",

            div(cls:="form-group mb-2",
              div(cls:="mb-3",
                label(`for`:="fileDescr", "Description"),
                textarea(cls:="form-control", id:="fileDescrEditFileModal", rows:="8", placeholder:="A more verbose description of the file",
                  onkeyup:= { (event: Event) =>
                    currentlyOpenedFile match {
                      case Some(f) =>
                        if ($("#fileDescrEditFileModal").`val`().toString() != f.description)
                          $("#saveFileDescrEditFileModal").removeAttr("disabled")
                        else
                          $("#saveFileDescrEditFileModal").attr("disabled", true)
                      case None => ;
                    }
                  })
              ),
              div(cls:="form-row",
                div(cls:="col"),
                div(cls:="col-2",
                  button(cls:="btn mb-2 mr-2", id:="saveFileDescrEditFileModal", data.toggle:="modal", data.dismiss:="modal", disabled:=true,
                    onclick:= { (event: Event) =>
                      currentlyOpenedFile match {
                        case Some(f) =>
                          HttpRequest(serverUrl() + "/updateFileDescription")
                            .post(MultiPartBody(
                              "filedescr"  -> PlainTextBody($("#fileDescrEditFileModal").`val`().toString().trim()),
                              "fileheader" -> PlainTextBody(f.header),
                              "memberId"   -> PlainTextBody(currentlyActiveMemberId.toString())))
                        case None => ;
                      }
                      $("#saveFileDescrEditFileModal").attr("disabled", true)
                      js.eval("$('#editFileModal').modal('hide');") // TODO: This is ugly! No idea for an alternative :-(
                    },
                    "Save"),
                  button(cls:="btn mb-2", data.dismiss:="modal", "Cancel")
                ),
                div(cls:="col")
              )
            ),

            div(cls:="border-top my-3"),

            div(cls:="form-group",
              div(
                label(`for`:="editFileAvailableFilesList", "Cases"),
                div(
                  cls:="list-group", role:="tablist", id:="editFileAvailableCasesList", style:=Rx("height: " + casesHeight().toString() + "px; overflow-y: scroll;"),
                  caseAnchors
                )
              ),
              div(cls:="form-row",
                div(cls:="col"),
                div(cls:="col-2",
                  button(cls:="btn mb-2 mr-2", id:="openFileEditFileModal", data.toggle:="modal", data.dismiss:="modal", disabled:=true,
                    onclick:={ (event: Event) =>
                      getCaseFromCurrentSelection()

                      // TODO: Get Caze from backend, then set Repertorise.results accordingly. TRY OUT!!!!!!!!!!!!!!!!
                      HttpRequest(serverUrl() + "/case")
                        .withQueryParameters(("memberId", currentlyActiveMemberId.toString()), ("caseId", currentlySelectedCaseId.now.toString()))
                        .withCrossDomainCookies(true)
                        .send()
                        .onComplete({
                          case response: Success[SimpleHttpResponse] => {
                            parse(response.get.body) match {
                              case Right(json) => {
                                val cursor = json.hcursor
                                cursor.as[Caze] match {
                                  case Right(caze) => {
                                    Case.descr = Some(caze)
                                    Case.cRubrics ++= caze.results
                                    // Repertorise.results() = caze.results // Important to set results(), because this triggers redraw of Repertorise()
                                    Repertorise.showResults()
                                  }
                                  case Left(err) => println("Decoding of case failed: " + err)
                                }
                              }
                              case Left(err) => println("Parsing of case (is it JSON?): " + err)
                            }
                          }
                          case error: Failure[SimpleHttpResponse] => println("Lookup of case failed: " + error.toString())
                        })
                    },
                    "Open"),
                  button(cls:="btn mb-2", id:="deleteFileEditFileModal", data.toggle:="modal", data.dismiss:="modal", data.target:="#editFileModalAreYouSureCase", disabled:=true,
                    onclick:= { (event: Event) => getCaseFromCurrentSelection() },
                    "Delete")
                ),
                div(cls:="col")
              )
            )

          )
        )
      )
    )
  }

  def apply() = div(areYouSureModalCase(), mainModal())

}
