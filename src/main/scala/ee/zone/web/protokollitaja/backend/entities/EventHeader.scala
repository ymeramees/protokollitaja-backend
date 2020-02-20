package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object EventHeader {
//  def apply(eventName: String): EventHeader = {
//    EventHeader(new ObjectId(), eventName)
//  }
//}

case class EventHeader(
                        id: String,
                        eventName: String
                      )
