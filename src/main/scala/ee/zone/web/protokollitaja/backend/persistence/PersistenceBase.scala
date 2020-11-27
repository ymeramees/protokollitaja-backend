package ee.zone.web.protokollitaja.backend.persistence

import com.mongodb.client.result.UpdateResult
import ee.zone.web.protokollitaja.backend.entities._
import org.mongodb.scala._

import scala.concurrent.Future

abstract class PersistenceBase {

  def getCompetition(competitionId: String): Future[Option[Competition]]

  def getCompetitionHeaders: Future[Seq[CompetitionHeader]]

  def getCompetitorsData(listName: String): Future[Seq[DBCompetitor]]

  def getCompetitorsDataVersion(listName: String): Future[Int]

  def getEventHeaders(competitionId: String): Future[Seq[EventHeader]]

  def getEventCompetitors(competitionId: String, eventId: String): Future[Seq[Competitor]]

  def getEventsLoadCount: Future[Int]

  def changeUserPassword(username: String, oldPassword: String, newPassword: String): Future[UpdateResult]

  def saveCompetition(newCompetition: Competition): Future[Completed]

  def updateCompetition(newCompetition: Competition): Future[Competition]

  def saveCompetitorsData(listName: String, competitors: Seq[DBCompetitor]): Future[Boolean]

  def saveUser(newUser: User): Future[Completed]

  def getPasswordAndAccessLevel(username: String): Future[Option[(String, Int)]]

  def getAllUsers: Future[Seq[User]]

}
