package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object Series {
//  def apply(seriesSum: String): Series = {
//    Series(/*new ObjectId(), */seriesSum)
//  }
//}

case class Series(
//                   _id: ObjectId,
                   seriesSum: String
                 )
