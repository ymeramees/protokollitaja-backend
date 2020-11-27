package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

object CompetitorsData {
  def apply(listName: String, version: Int, competitors: Seq[DBCompetitor]): CompetitorsData =
    new CompetitorsData(new ObjectId(), listName, version, competitors)
}

case class CompetitorsData(
                            _id: ObjectId,
                            listName: String,
                            version: Int,
                            competitors: Seq[DBCompetitor]
                          )
