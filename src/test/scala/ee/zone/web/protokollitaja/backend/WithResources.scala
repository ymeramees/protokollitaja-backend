package ee.zone.web.protokollitaja.backend

import com.mongodb.client.result.UpdateResult
import ee.zone.web.protokollitaja.backend.entities._
import ee.zone.web.protokollitaja.backend.persistence.PersistenceBase
import org.json4s.jackson.JsonMethods.parse
import org.json4s.mongo.ObjectIdSerializer
import org.json4s.{DefaultFormats, Formats, JValue}
import org.mongodb.scala.Completed

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

trait WithResources {

  def withCompetitionJsonFile(fileName: String)(testCode: JValue => Any): Any = {
    implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer
    val resource = Source.fromURL(getClass.getClassLoader.getResource(fileName))
    val json = parse(resource.mkString).transformField { // Hack to serialize _id to ObjectId
      case ("id", s: JValue) =>
        val idString = s.extract[String]
        if (idString.startsWith("5e") && idString.length == 24) {
          ("_id", parse("{\"$oid\":\"" + s.extract[String] + "\"}"))
        } else {
          ("_id", s)
        }
    }
    try {
      testCode(json)
    } finally {
      resource.close
    }
  }

  def withTextFile(fileName: String)(testCode: String => Any): Any = {
    val resource = Source.fromURL(getClass.getClassLoader.getResource(fileName))
    val txt = resource.mkString
    try {
      testCode(txt)
    } finally {
      resource.close
    }
  }

  class DummyPersistence(
                          competitionHeadersList: List[CompetitionHeader],
                          eventHeadersList: List[EventHeader],
                          eventCompetitors: Seq[Competitor],
                          competitorsData: List[DBCompetitor]
                        )(implicit execContext: ExecutionContext) extends PersistenceBase {

    def getCompetition(competitionId: String): Future[Option[Competition]] = ???

    def getCompetitionHeaders: Future[Seq[CompetitionHeader]] = Future.successful(competitionHeadersList)

    def getCompetitorsData(listName: String): Future[Seq[DBCompetitor]] = Future.successful(competitorsData)

    def getCompetitorsDataVersion(listName: String): Future[Int] = Future.successful(3)

    def getEventHeaders(competitionId: String): Future[Seq[EventHeader]] = Future.successful(eventHeadersList)

    def getEventCompetitors(competitionId: String, eventId: String): Future[Seq[Competitor]] = Future.successful(eventCompetitors)

    def getEventsLoadCount: Future[Int] = Future.successful(11)

    def changeUserPassword(username: String, oldPassword: String, newPassword: String): Future[UpdateResult] = ???

    def saveCompetition(newCompetition: Competition): Future[Completed] = Future(Completed())

    def updateCompetition(newCompetition: Competition): Future[Competition] = Future(newCompetition)

    def saveCompetitorsData(listName: String, competitors: Seq[DBCompetitor]): Future[Boolean] = Future.successful(true)

    def saveUser(newUser: User): Future[Completed] = ???

    def getPasswordAndAccessLevel(username: String): Future[Option[(String, Int)]] = {
      username match {
        case "TestLvl0User" => Future(Some("$2a$10$JuQdI2SjbZ8cebaNT..Juui5pVjP8FWQEXXds/EvHhZj317RTXxhy", 0))
        case "TestNormalUser" => Future(Some("$2a$10$JuQdI2SjbZ8cebaNT..Juui5pVjP8FWQEXXds/EvHhZj317RTXxhy", 1))
        case "TestLvl2User" => Future(Some("$2a$10$JuQdI2SjbZ8cebaNT..Juui5pVjP8FWQEXXds/EvHhZj317RTXxhy", 2))
        case _ => Future(None)
      }
    }

    def getAllUsers: Future[Seq[User]] = ???
  }

}
