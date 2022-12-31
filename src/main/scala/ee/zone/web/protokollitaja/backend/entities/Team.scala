package ee.zone.web.protokollitaja.backend.entities

case class Team(
                 teamName: String,
                 teamMembers: List[TeamCompetitor],
                 totalResult: String,
                 remarks: String
               )
