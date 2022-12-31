package ee.zone.web.protokollitaja.backend.entities

case class Event(
                  _id: String,
                  eventName: String,
                  competitors: List[Competitor],
                  teams: Option[List[Team]]
                )
