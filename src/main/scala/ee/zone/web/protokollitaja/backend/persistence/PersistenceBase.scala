package ee.zone.web.protokollitaja.backend.persistence

import com.mongodb.client.result.UpdateResult
import ee.zone.web.protokollitaja.backend.entities._
import org.mongodb.scala._

import scala.concurrent.Future

abstract class PersistenceBase {

  def getCompetition(competitionId: String): Future[Option[Competition]]

  def getCompetitionHeaders: Seq[CompetitionHeader]

  def getEventHeaders(competitionId: String): Seq[EventHeader]

  def getEventCompetitors(competitionId: String, eventId: String): Seq[Competitor]

  def getEventsLoadCount: Int

  def changeUserPassword(username: String, oldPassword: String, newPassword: String): Future[UpdateResult]

  def saveCompetition(newCompetition: Competition): Future[Completed]

  def updateCompetition(newCompetition: Competition): Future[Competition]

  def saveUser(newUser: User): Future[Completed]

  def getPasswordAndAccessLevel(username: String): Future[Option[(String, Int)]]

  def getAllUsers: Future[Seq[User]]

}
