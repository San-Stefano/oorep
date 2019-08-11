package org.multics.baueran.frep.backend.dao

import java.sql.{Connection, PreparedStatement, ResultSet}

import io.getquill
import org.multics.baueran.frep.shared._
import org.multics.baueran.frep.backend.db
import Defs.maxNumberOfResults
import play.api.Logger

import scala.collection.mutable.ArrayBuffer

class RepertoryDao(dbContext: db.db.DBContext) {

  import dbContext._

  private val tableInfo    = quote { querySchema[Info]("Info", _.abbrev -> "abbrev") }
  private val tableChapter = quote { querySchema[Chapter]("Chapter", _.text -> "textt") }

  def getInfo(abbrev: String) = {
    implicit val decodeRepAccess = MappedEncoding[String, RepAccess.RepAccess](RepAccess.withName(_))

    val select = quote { tableInfo.filter(_.abbrev == lift(abbrev)) }
    run(select)
  }

  def insert(info: Info) = {
    implicit val encodeRepAccess = MappedEncoding[RepAccess.RepAccess, String](_.toString())

    val insert = quote { tableInfo.insert(lift(info)) }
    run(insert)
  }

  def getAllAvailableRepertoryInfos() = {
    implicit val decodeRepAccess = MappedEncoding[String, RepAccess.RepAccess](RepAccess.withName(_))
    run(quote(tableInfo.filter(_ => true)))
  }

  def getChapter(chapterId: Int) = {
    val get = quote{ tableChapter.filter(_.id == lift(chapterId)) }
    run(get)
  }

  def insert(chapter: Chapter) = {
    val insert = quote{ tableChapter.insert(lift(chapter)) }
    run(insert)
  }

  def insert(rr: RubricRemedy) = {
    val insert = quote(query[RubricRemedy].insert(lift(rr)))
    run(insert)
  }

  def getRubricRemedy(rr: RubricRemedy) = {
    val get = quote(query[RubricRemedy].filter(rubricRemedy => {
      rubricRemedy.remedyId == lift(rr.remedyId) &&
        rubricRemedy.abbrev == lift(rr.abbrev) &&
        rubricRemedy.chapterId == lift(rr.chapterId) &&
        rubricRemedy.rubricId == lift(rr.rubricId)
    }))
    run(get)
  }

  def insert(r: Remedy) = {
    val insert = quote(query[Remedy].insert(lift(r)))
    run(insert)
  }

  def getRemedy(abbrev: String, id: Int) = {
    val get = quote(query[Remedy].filter(remedy => remedy.abbrev == lift(abbrev) && remedy.id == lift(id)))
    run(get)
  }

  def insert(cr: ChapterRemedy) = {
    val insert = quote(query[ChapterRemedy].insert(lift(cr)))
    run(insert)
  }

  def getChapterRemedy(cr: ChapterRemedy) = {
    val get = quote(query[ChapterRemedy].filter(chapterRemedy => {
      chapterRemedy.abbrev == lift(cr.abbrev) &&
        chapterRemedy.chapterId == lift(cr.chapterId) &&
        chapterRemedy.remedyId == lift(cr.remedyId)
    }))
    run(get)
  }

  def insert(r: Rubric) = {
    val insert = quote(query[Rubric].insert(lift(r)))
    run(insert)
  }

  def getRubric(r: Rubric) = {
    val get = quote(query[Rubric].filter(rubric =>
      rubric.abbrev == lift(r.abbrev) && rubric.id == lift(r.id)
    ))
    run(get)
  }

  def lookupSymptom(abbrev: String, symptom: String): List[CaseRubric] = {
    val searchStrings = symptom.
      trim.                                                    // Remove trailing spaces
      replaceAll(" +", " ").              // Remove double spaces
      replaceAll("[^A-Za-z0-9 \\-*]", "").// Remove all but alphanum-, wildcard-, minus-symbols
      split(" ")                                       // Get list of search strings

    val posSearchTerms = searchStrings.filter(!_.startsWith("-")).toList
    val negSearchTerms = searchStrings.filter(_.startsWith("-")).map(_.substring(1)).toList

    if (posSearchTerms.length == 0) {
      Logger.warn(s"INFO: Search for `$symptom' aborted: no positive search terms.")
      return List()
    }

    // TODO: use of approximateSearchTerm is an oversimplification to narrow down the first
    // DB-lookup, which otherwise would return ALWAYS the entire repertory.
    val approximateSearchTerm = "%" + posSearchTerms.head.toLowerCase.replaceAll("[^A-Za-z0-9 \\-]", "") + "%"

    val tmpResults =
      run(
        quote {
          query[Rubric]
            .filter(rubric =>
              rubric.abbrev == lift(abbrev) &&
                rubric.chapterId >= 0 &&
                (rubric.fullPath.like(lift(approximateSearchTerm)) ||
                  rubric.textt.getOrElse("").like(lift(approximateSearchTerm)) ||
                  rubric.path.getOrElse("").like(lift(approximateSearchTerm)))
            )
        }
      ).filter(_.isMatchFor(posSearchTerms, negSearchTerms))
        .sortBy(_.fullPath)
        .take(maxNumberOfResults)

    val results =
      run(
        quote {
          for {
            rubrics <- query[Rubric].filter(rubric => rubric.abbrev == lift(abbrev) && liftQuery(tmpResults.map(_.id)).contains(rubric.id))
            remedies <- query[Remedy].join(remedy => remedy.abbrev == rubrics.abbrev)
            rr <- query[RubricRemedy].join(r => r.remedyId == remedies.id && r.rubricId == rubrics.id && r.abbrev == rubrics.abbrev)
          } yield (rubrics, remedies, rr)
        }
      )

    def getWeightedRemedies(rubric: Rubric) = {
      results
        .filter(_._1 == rubric)
        .map { case (_, rem, rr) => WeightedRemedy(rem, rr.weight) }
    }

    results
      .map { case (rubric, _, _) => rubric }
      .distinct
      .map { rubric => CaseRubric(rubric, abbrev, 1, getWeightedRemedies(rubric)) }
      .sortBy { _.rubric.fullPath }
  }

  def getRemediesForRubric(rubric: Rubric): Seq[(Remedy, Int)] = {
    var result: ArrayBuffer[(Remedy, Int)] = new ArrayBuffer[(Remedy,Int)]
    val filter = quote { query[RubricRemedy].filter(rr => rr.rubricId == lift(rubric.id) && rr.abbrev == lift(rubric.abbrev)) }
    val remedyIdWeightTuples: Seq[(Int, Int)] = run(filter).map(rr => (rr.remedyId, rr.weight))

    remedyIdWeightTuples.foreach { case (rid, rweight) =>
      val allRemedies = quote { query[Remedy].filter(r => r.abbrev == lift(rubric.abbrev)) }
      run(allRemedies).find(_.id == rid) match {
        case Some(remedy) => result += ((remedy, rweight))
        case None => Logger.warn("WARNING: RepertoryDao.getRemediesForRubric: No remedy found.")
      }
    }

    result
  }

  def insert(cr: CaseRubric) = {
    implicit val encodeCaseRubric = MappedEncoding[CaseRubric, String](_.toString())

    val insert = quote(query[CaseRubric].insert(lift(cr)))
    run(insert)
  }

  def insert(c: org.multics.baueran.frep.shared.Caze) = {
    implicit val encodeCase = MappedEncoding[org.multics.baueran.frep.shared.Caze, String](_.toString())

    val insert = quote(query[org.multics.baueran.frep.shared.Caze].insert(lift(c)))
    run(insert)
  }

}
