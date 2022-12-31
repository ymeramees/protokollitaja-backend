package ee.zone.web.protokollitaja.backend.entities

case class Competitor(
                       _id: String,
                       firstName: String,
                       lastName: String,
                       birthYear: String,
                       club: String,
                       subtotals: Option[List[Subtotal]],
                       series: Option[List[Series]], // Not needed here in new format
                       totalResult: String,
                       innerTens: Option[String],
                       finals: String,
                       remarks: Option[String]
                     )
