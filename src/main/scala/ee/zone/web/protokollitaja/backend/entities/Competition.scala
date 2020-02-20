package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

object Competition {
  def apply(competitionName: String, timeAndPlace: String, events: List[Event]): Competition = {
    new Competition(new ObjectId(), competitionName, timeAndPlace, events)
  }

  def apply(_id: String, competitionName: String, timeAndPlace: String, events: List[Event]): Competition = {
    new Competition(new ObjectId(_id), competitionName, timeAndPlace, events)
  }
}

case class Competition(
                        _id: ObjectId,
                        competitionName: String,
                        timeAndPlace: String,
                        events: List[Event]
                      )
