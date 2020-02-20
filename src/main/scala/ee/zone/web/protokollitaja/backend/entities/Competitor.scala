package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

//object Competitor {
//  def apply(
//             _id: ObjectId,
//             firstName: String,
//             lastName: String,
//             birthYear: String,
//             club: String,
//             subtotals: List[Subtotals],
//             series: Option[List[Series]],
//             totalResult: String,
//             innerTens: String,
//             finals: String,
//             remarks: String
//           ): Competitor = {
//    Competitor(
////      new ObjectId(),
//      _id.toString,
//      firstName,
//      lastName,
//      birthYear,
//      club,
//      subtotals,
//      series,
//      totalResult,
//      innerTens,
//      finals,
//      remarks
//    )
//  }
//}

case class Competitor(
                       _id: String,
                       firstName: String,
                       lastName: String,
                       birthYear: String,
                       club: String,
                       subtotals: Option[List[Subtotals]],
                       series: Option[List[Series]], // Not needed here in new format
                       totalResult: String,
                       innerTens: Option[String],
                       finals: String,
                       remarks: Option[String]
                     )
