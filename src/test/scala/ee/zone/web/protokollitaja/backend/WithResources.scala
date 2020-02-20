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
                          eventCompetitors: Seq[Competitor]
                        )(implicit execContext: ExecutionContext) extends PersistenceBase {

    def getCompetition(competitionId: String): Future[Option[Competition]] = ???

    def getCompetitionHeaders: Seq[CompetitionHeader] = competitionHeadersList

    def getEventHeaders(competitionId: String): Seq[EventHeader] = eventHeadersList

    def getEventCompetitors(competitionId: String, eventId: String): Seq[Competitor] = eventCompetitors

    def getEventsLoadCount: Int = 11

    def changeUserPassword(username: String, oldPassword: String, newPassword: String): Future[UpdateResult] = ???

    def saveCompetition(newCompetition: Competition): Future[Completed] = Future(Completed())

    def updateCompetition(newCompetition: Competition): Future[Competition] = Future(newCompetition)

    def saveUser(newUser: User): Future[Completed] = ???

    def getPasswordAndAccessLevel(username: String): Future[Option[(String, Int)]] = {
      username match {
        case "Someuser34" => Future(Some("$2a$10$JuQdI2SjbZ8cebaNT..Juui5pVjP8FWQEXXds/EvHhZj317RTXxhy", 1))
        case _ => Future(None)
      }
    }

    def getAllUsers: Future[Seq[User]] = ???

    //  def withApiServer(dummyPersistence: PersistenceBase)(testCode: PersistenceBase => Any): Any = {
    //    TestKit.spawn()
    //    try {
    //      testCode(dummyPersistence)
    //    }
  }

}
