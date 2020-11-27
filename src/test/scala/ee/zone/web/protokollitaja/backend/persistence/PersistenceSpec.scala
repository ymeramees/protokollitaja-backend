package ee.zone.web.protokollitaja.backend.persistence

import com.github.t3hnar.bcrypt._
import ee.zone.web.protokollitaja.backend.entities.{Competition, CompetitorsData, DBCompetitor, User}
import ee.zone.web.protokollitaja.backend.{WithDatabase, WithResources}
import org.json4s._
import org.json4s.mongo.ObjectIdSerializer
import org.mongodb.scala.bson.ObjectId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class PersistenceSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with WithResources with WithDatabase {

  implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "Persistence" should {
    "save new user to the database and return its password from there" in withDatabase { persistence =>
      val user = User("testuser", "someHashedPassword", 1)

      Await.result(persistence.saveUser(user), 1.second)

      val password = Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second).get

      password should not be user.password
      user.password.isBcryptedSafe(password._1).getOrElse(false) shouldBe true
    }

    "not save duplicate usernames into database" in withDatabase { persistence =>
      val user1 = User("testuser", "someHashedPassword1", 1)
      val user2 = User("testuser", "someHashedPassword2", 1)

      Await.result(persistence.saveUser(user1), 1.second)

      val exception2 = intercept[RuntimeException] {
        Await.result(persistence.saveUser(user2), 1.second)
      }
      exception2.getMessage shouldBe "User with this username already exists!"

      val users = Await.result(persistence.getAllUsers, 1.second)

      users.length shouldBe 1
    }

    "change user password" in withDatabase { persistence =>
      val password = "someHashedPassword1"
      val user = User("testuser3", password, 1)
      val newPassword = "someNiceNewPass"

      Await.result(persistence.saveUser(user), 1.second)
      Await.result(persistence.changeUserPassword(user.username, password, newPassword), 2.second)

      val changedPassword = persistence.getPasswordAndAccessLevel(user.username).futureValue
      changedPassword.get should not be password
      newPassword.isBcryptedSafe(changedPassword.get._1).getOrElse(false) shouldBe true
    }

    "respond with an error to change user password if provided old password is wrong" in withDatabase { persistence =>
      val password = "someHashedPassword1"
      val user = User("testuser4", password, 1)
      val newPassword = "someNiceNewPass"

      Await.result(persistence.saveUser(user), 1.second)
      val exception = intercept[RuntimeException] {
        Await.result(persistence.changeUserPassword(user.username, "someWrongPassword", newPassword), 1.second)
      }
      exception.getMessage shouldBe "Username and/or old password do not match!"

      val dbPassword = persistence.getPasswordAndAccessLevel(user.username).futureValue
      dbPassword.get should not be password
      password.isBcryptedSafe(dbPassword.get._1).getOrElse(false) shouldBe true
    }

    "respond with an error to change user password if user does not exist" in withDatabase { persistence =>
      val password = "someHashedPassword1"
      val user = User("testuser5", password, 1)
      val newPassword = "someNiceNewPass"

      val exception = intercept[RuntimeException] {
        Await.result(persistence.changeUserPassword(user.username, "someWrongPassword", newPassword), 1.second)
      }
      exception.getMessage shouldBe "Username and/or old password do not match!"

      val dbPassword = persistence.getPasswordAndAccessLevel(user.username).futureValue
      dbPassword shouldBe None
    }

    "return user access level" in withDatabase { persistence =>
      val user = User("testuser6", "someHashedPassword", 2)

      Await.result(persistence.saveUser(user), 1.second)

      Await.result(persistence.getPasswordAndAccessLevel(user.username), 1.second).get._2 shouldBe 2
    }

    "return empty Seq if there are no competitions in the DB" in withDatabase { persistence =>
      val headers = persistence.getCompetitionHeaders.futureValue
      headers.length shouldBe 0
    }

    "save and return a competition" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)
          val competitors = persistence.getEventCompetitors("5e1f495e4e48bd44eba2550b", "5").futureValue
          competitors.length shouldBe 13
          competitors.head.firstName shouldBe "Katrin"
      }
    }

    "respond with an error if competition is being saved twice" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          val exception = intercept[RuntimeException] {
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          exception.getMessage shouldBe s"Competition with id ${competition._id} already existing! Use PUT request to update an existing competition!"
      }
    }

    "respond with an error if competition with an existing name is being saved" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          val exception = intercept[RuntimeException] {
            Await.result(persistence.saveCompetition(competition.copy(_id = new ObjectId)), 1.second)
          }
          exception.getMessage shouldBe s"Competition with same name already existing, id: ${competition._id}!"
      }
    }

    "respond with an error if a new competition id is being updated" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          val exception = intercept[RuntimeException] {
            Await.result(persistence.updateCompetition(competition), 1.second)
          }
          exception.getMessage shouldBe s"Competition with id ${competition._id} not existing! Use POST request to save new competition!"
      }
    }

    "update and return updated competition" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          val competition = json.extract[Competition]

          Await.result(persistence.saveCompetition(competition), 1.second)

          Await.result(persistence.updateCompetition(competition.copy(competitionName = "Test Competition", events = List())), 1.second)

          val newCompetition = Await.result(persistence.getCompetition(competition._id), 1.second)
          newCompetition.get.competitionName shouldBe "Test Competition"
          newCompetition.get.events.length shouldBe 0
      }
    }

    "save and return competitors data list" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitorsData.json") { json =>
        val competitorsData = json.extract[Seq[DBCompetitor]]

        persistence.saveCompetitorsData("rifle", competitorsData).futureValue shouldBe true

        val result = persistence.getCompetitorsData("rifle").futureValue
        result shouldBe competitorsData
      }
    }

    "save competitors data list and return initial version" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitorsData.json") { json =>
        val competitorsData = json.extract[Seq[DBCompetitor]]

        persistence.saveCompetitorsData("rifle", competitorsData).futureValue shouldBe true

        persistence.getCompetitorsDataVersion("rifle").futureValue shouldBe 1
      }
    }

    "update and return competitors data list" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitorsData.json") { json =>
        val competitorsData = json.extract[Seq[DBCompetitor]]

        persistence.saveCompetitorsData("rifle", competitorsData).futureValue shouldBe true

        val newCompetitors = competitorsData :+ DBCompetitor("Paul", "Põld", 2009, "Korstnapühkijad", None, None, 0)

        persistence.saveCompetitorsData("rifle", newCompetitors).futureValue shouldBe true

        val result = persistence.getCompetitorsData("rifle").futureValue
        result shouldBe newCompetitors
      }
    }

    "update list and return increased competitors data list version" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitorsData.json") { json =>
        val competitorsData = json.extract[Seq[DBCompetitor]]


        persistence.saveCompetitorsData("rifle", competitorsData).futureValue shouldBe true

        val newCompetitors = competitorsData :+ DBCompetitor("Paul", "Põld", 2009, "Korstnapühkijad", None, None, 0)

        persistence.saveCompetitorsData("rifle", newCompetitors).futureValue shouldBe true

        persistence.getCompetitorsDataVersion("rifle").futureValue shouldBe 2
      }
    }

    "return competition headers" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val headers = persistence.getCompetitionHeaders.futureValue
          headers.length shouldBe 2

          headers.head.id shouldBe competitions.head._id.toString
          headers.head.competitionName shouldBe competitions.head.competitionName
          headers.head.timeAndPlace shouldBe competitions.head.timeAndPlace

          headers.last.id shouldBe competitions.last._id.toString
          headers.last.competitionName shouldBe competitions.last.competitionName
          headers.last.timeAndPlace shouldBe competitions.last.timeAndPlace
      }
    }

    "return empty Seq if competition Id was not found" in withDatabase { persistence =>
      val headers = persistence.getEventHeaders("5e22ef4c072cb80d41fb6adf").futureValue
      headers.length shouldBe 0
    }

    "return competition events' headers" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val eventHeaders = persistence.getEventHeaders(competitions.head._id.toString).futureValue
          eventHeaders.length shouldBe 4

          eventHeaders.head.id shouldBe competitions.head.events.head._id
          eventHeaders.head.eventName shouldBe competitions.head.events.head.eventName

          eventHeaders.last.id shouldBe competitions.head.events.last._id
          eventHeaders.last.eventName shouldBe competitions.head.events.last.eventName
      }
    }

    "return requested event" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }
          val competitors = persistence.getEventCompetitors("5e1ae2cc4cfa124b351b0954", "3").futureValue
          competitors.length shouldBe 18
          competitors.head.firstName shouldBe "Joosep robin"
      }
    }

    "keep and increase events load count" in withDatabase { persistence =>
      withCompetitionJsonFile("testdata/competitions.json") {
        json =>
          val competitions = json.extract[List[Competition]]

          competitions.foreach { competition =>
            Await.result(persistence.saveCompetition(competition), 1.second)
          }

          persistence.getEventsLoadCount.futureValue shouldBe 0

          val competitors = persistence.getEventCompetitors("5e1ae2cc4cfa124b351b0954", "3").futureValue
          competitors.length shouldBe 18

          persistence.getEventsLoadCount.futureValue shouldBe 1
      }
    }
  }
}
