package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object CompetitionHeader {
//  def apply(id: String, competitionName: String, timeAndPlace: String): CompetitionHeader = {
//    CompetitionHeader(id, competitionName, timeAndPlace)
//  }
//}

case class CompetitionHeader(
                              id: String,
                              competitionName: String,
                              timeAndPlace: String
                            )
