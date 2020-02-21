package ee.zone.web.protokollitaja.backend.persistence

import com.github.t3hnar.bcrypt._
import com.typesafe.config.ConfigFactory
import ee.zone.web.protokollitaja.backend.WithResources
import ee.zone.web.protokollitaja.backend.entities.{Competition, User}
import org.json4s._
import org.json4s.mongo.ObjectIdSerializer
import org.mongodb.scala.bson.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class PersistenceSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with WithResources {

  implicit val formats = DefaultFormats + new ObjectIdSerializer

  val config = ConfigFactory.load()
  val persistence = new Persistence(config)

  override def afterAll() = {
    persistence.cleanUpDatabase()
  }

  "Persistence" should {
    "save new user to the database and return its password from there" in {
      val user = User("testuser", "someHashedPassword", 1)

      Await.result(persistence.saveUser(user), 1.second)

      val password = Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second).get

      password should not be user.password
      user.password.isBcryptedSafe(password._1).getOrElse(false) shouldBe true
    }

    "not save duplicate usernames into database" in {
      val user1 = User("testuser", "someHashedPassword1", 1)
      val user2 = User("testuser", "someHashedPassword2", 1)

      val exception = intercept[RuntimeException] {
        Await.result(persistence.saveUser(user1), 1.second)
      }
      exception.getMessage shouldBe "User with this username already exists!"

      val exception2 = intercept[RuntimeException] {
        Await.result(persistence.saveUser(user2), 1.second)
      }
      exception2.getMessage shouldBe "User with this username already exists!"

      val users = Await.result(persistence.getAllUsers, 1.second)

      users.length shouldBe 1
    }

    "change user password" in {
      val password = "someHashedPassword1"
      val user = User("testuser3", password, 1)
      val newPassword = "someNiceNewPass"

      Await.result(persistence.saveUser(user), 1.second)
      Await.result(persistence.changeUserPassword(user.username, password, newPassword), 1.second)

      val changedPassword = Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second)
      changedPassword.get should not be password
      newPassword.isBcryptedSafe(changedPassword.get._1).getOrElse(false) shouldBe true
    }

    "respond with an error to change user password if provided old password is wrong" in {
      val password = "someHashedPassword1"
      val user = User("testuser4", password, 1)
      val newPassword = "someNiceNewPass"

      Await.result(persistence.saveUser(user), 1.second)
      val exception = intercept[RuntimeException] {
        Await.result(persistence.changeUserPassword(user.username, "someWrongPassword", newPassword), 1.second)
      }
      exception.getMessage shouldBe "Username and/or old password do not match!"

      val dbPassword = Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second)
      dbPassword.get should not be password
      password.isBcryptedSafe(dbPassword.get._1).getOrElse(false) shouldBe true
    }

    "respond with an error to change user password if user does not exist" in {
      val password = "someHashedPassword1"
      val user = User("testuser5", password, 1)
      val newPassword = "someNiceNewPass"

      val exception = intercept[RuntimeException] {
        Await.result(persistence.changeUserPassword(user.username, "someWrongPassword", newPassword), 1.second)
      }
      exception.getMessage shouldBe "Username and/or old password do not match!"

      val dbPassword = Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second)
      dbPassword shouldBe None
    }

    "return user access level" in {
      val user = User("testuser6", "someHashedPassword", 2)

      Await.result(persistence.saveUser(user), 1.second)

      Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second).get._2 shouldBe 2
    }

    "return empty Seq if there are no competitions in the DB" in {
      val headers = persistence.getCompetitionHeaders
      headers.length shouldBe 0
    }

    "save and return a competition" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)
          val competitors = persistence.getEventCompetitors("5e1f495e4e48bd44eba2550b", "5")
          competitors.length shouldBe 13
          competitors.head.firstName shouldBe "Katrin"
      }
      persistence.cleanUpDatabase()
    }

    "respond with an error if competition is being saved twice" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          val exception = intercept[RuntimeException] {
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          exception.getMessage shouldBe s"Competition with id ${competition._id} already existing! Use PUT request to update an existing competition!"
      }
      persistence.cleanUpDatabase()
    }

    "respond with an error if competition with an existing name is being saved" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          val exception = intercept[RuntimeException] {
            Await.result(persistence.saveCompetition(competition.copy(_id = new ObjectId)), 1.second)
          }
          exception.getMessage shouldBe s"Competition with same name already existing, id: ${competition._id}!"
      }
      persistence.cleanUpDatabase()
    }

    "respond with an error if a new competition id is being updated" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          val exception = intercept[RuntimeException] {
            Await.result(persistence.updateCompetition(competition), 1.second)
          }
          exception.getMessage shouldBe s"Competition with id ${competition._id} not existing! Use POST request to save new competition!"
      }
      persistence.cleanUpDatabase()
    }

    "update and return updated competition" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          Await.result(persistence.updateCompetition(competition.copy(competitionName = "Test Competition", events = List())), 1.second)

          val newCompetition = Await.result(persistence.getCompetition(competition._id), 1.second)
          newCompetition.get.competitionName shouldBe "Test Competition"
          newCompetition.get.events.length shouldBe 0
      }
      persistence.cleanUpDatabase()
    }

    "return competition headers" in {
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val headers = persistence.getCompetitionHeaders
          headers.length shouldBe 2

          headers.head.id shouldBe competitions.head._id.toString
          headers.head.competitionName shouldBe competitions.head.competitionName
          headers.head.timeAndPlace shouldBe competitions.head.timeAndPlace

          headers.last.id shouldBe competitions.last._id.toString
          headers.last.competitionName shouldBe competitions.last.competitionName
          headers.last.timeAndPlace shouldBe competitions.last.timeAndPlace
      }
      persistence.cleanUpDatabase()
    }

    "return empty Seq if competition Id was not found" in {
      val headers = persistence.getEventHeaders("5e22ef4c072cb80d41fb6adf")
      headers.length shouldBe 0
    }

    "return competition events' headers" in {
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val eventHeaders = persistence.getEventHeaders(competitions.head._id.toString)
          eventHeaders.length shouldBe 4

          eventHeaders.head.id shouldBe competitions.head.events.head._id
          eventHeaders.head.eventName shouldBe competitions.head.events.head.eventName

          eventHeaders.last.id shouldBe competitions.head.events.last._id
          eventHeaders.last.eventName shouldBe competitions.head.events.last.eventName
      }
      persistence.cleanUpDatabase()
    }

    "return requested event" in {
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val competitors = persistence.getEventCompetitors("5e1ae2cc4cfa124b351b0954", "3")
          competitors.length shouldBe 18
          competitors.head.firstName shouldBe "Joosep robin"
      }
      persistence.cleanUpDatabase()
    }

    "keep and increase events load count" in {
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }

          persistence.getEventsLoadCount shouldBe 0

          val competitors = persistence.getEventCompetitors("5e1ae2cc4cfa124b351b0954", "3")
          competitors.length shouldBe 18

          persistence.getEventsLoadCount shouldBe 1
      }
      persistence.cleanUpDatabase()
    }
  }
}
