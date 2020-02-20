package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object Event {
//  def apply(_id: ObjectId, eventName: String, competitors: List[Competitor]): Event = {
//    Event(_id.toString, eventName, competitors)
//  }
//
//  def apply(_id: String, eventName: String, competitors: List[Competitor]): Event = {
//    new Event(_id, eventName, competitors)
//  }
//}

case class Event(
                  _id: String,
                  eventName: String,
                  competitors: List[Competitor]
                )
