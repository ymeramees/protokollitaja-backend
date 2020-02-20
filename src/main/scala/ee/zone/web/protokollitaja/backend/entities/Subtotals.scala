package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object Subtotals {
//  def apply(series: List[Series], label: String, subtotal: String): Subtotals = {
//    Subtotals(/*new ObjectId(), */series, label, subtotal)
//  }
//}

case class Subtotals(
//                      _id: ObjectId,
                      series: List[Series],
                      label: String,
                      subtotal: String
                    )
