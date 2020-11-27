package ee.zone.web.protokollitaja.backend.entities

case class DBCompetitor(
                         firstName: String,
                         lastName: String,
                         birthYear: Int,
                         club: String,
                         county: Option[String],
                         militaryUnit: Option[String],
                         ordering: Int
                       )
