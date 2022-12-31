package ee.zone.web.protokollitaja.backend.server

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{MessageEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import ee.zone.web.protokollitaja.backend.WithResources
import ee.zone.web.protokollitaja.backend.entities.{CompetitionHeader, Competitor, DBCompetitor, EventHeader}
import ee.zone.web.protokollitaja.backend.protocol.BackendProtocol.{BackendMsg, GetRoute, SendRoute}
import org.json4s.DefaultFormats
import org.json4s.mongo.ObjectIdSerializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ApiServerSpec extends AnyFreeSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WithResources {
  val testKit = ActorTestKit()

  implicit val formats = DefaultFormats + new ObjectIdSerializer
  val config = ConfigFactory.load()
  private val persistence = new DummyPersistence(
    competitionHeadersList = List(CompetitionHeader("5e1ae2cc4cfa124b351b0954", "Eesti juunioride meistrivõistlus", "19. jaan. 2019 Rapla")),
    eventHeadersList = List(EventHeader("3", "60l Õhupüss Naised")),
    eventCompetitors = List(Competitor("324", "Paavel", "Pakiraam", "2019", "SharpshootersClub", None, Some(List()), "0,0", Some("0"), "Fin", Some(""))),
    competitorsData = List(
      DBCompetitor("Paul", "Põld", 2001, "Mighty Ducks", None, None, 0)
    )
  )

  private val parserDispatcher = system.dispatchers.lookup("parser-dispatcher")
  private val apiServer = testKit.spawn(ApiServer(persistence, parserDispatcher, config), "apiServer")
  private val probe = testKit.createTestProbe[BackendMsg]()
  apiServer ! GetRoute(probe.ref)
  private val route = probe.expectMessageType[SendRoute].route

  override def beforeAll(): Unit = {
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()


  "ApiServer" - {
    "respond to GET request with competition headers without authentication" in {
      val request = Get(s"/api/v1/competitions")

      request ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldEqual "[{\"id\":\"5e1ae2cc4cfa124b351b0954\",\"competitionName\":\"Eesti juunioride meistrivõistlus\",\"timeAndPlace\":\"19. jaan. 2019 Rapla\"}]"
      }
    }

    "respond to GET request with event headers without authentication" in {
      val request = Get(s"/api/v1/competitions/5e1ae2cc4cfa124b351b0954")

      request ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldEqual "[{\"id\":\"3\",\"eventName\":\"60l Õhupüss Naised\"}]"
      }
    }

    "respond to GET request with event without authentication" in {
      val request = Get(s"/api/v1/competitions/5e1ae2cc4cfa124b351b0954/4")

      request ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldEqual "{\"id\":\"4\",\"eventName\":\"TestEvent\",\"competitors\":[{\"id\":\"324\",\"firstName\":\"Paavel\",\"lastName\":\"Pakiraam\",\"birthYear\":\"\",\"club\":\"SharpshootersClub\",\"series\":[],\"totalResult\":\"0,0\",\"innerTens\":\"0\",\"finals\":\"Fin\",\"remarks\":\"\"}],\"teams\":[]}"
      }
    }

    "return competitors without birth year" in {
      val request = Get(s"/api/v1/competitions/5e1ae2cc4cfa124b351b0954/4")

      request ~> route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldEqual "{\"id\":\"4\",\"eventName\":\"TestEvent\",\"competitors\":[{\"id\":\"324\",\"firstName\":\"Paavel\",\"lastName\":\"Pakiraam\",\"birthYear\":\"\",\"club\":\"SharpshootersClub\",\"series\":[],\"totalResult\":\"0,0\",\"innerTens\":\"0\",\"finals\":\"Fin\",\"remarks\":\"\"}],\"teams\":[]}"
      }
    }

    "respond to authenticated POST request and save the competition" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val credentials = BasicHttpCredentials("TestNormalUser", "SomeRandomPass")
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Post(s"/api/v1/competitions")
            .withEntity(entity)
            .addCredentials(credentials)

          request ~> route ~> check {
            status shouldBe StatusCodes.Created
            responseAs[String] shouldEqual "5e1f495e4e48bd44eba2550b"
          }
      }
    }

    "respond to authenticated PUT request and update the competition" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val credentials = BasicHttpCredentials("TestNormalUser", "SomeRandomPass")
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Put(s"/api/v1/competitions")
            .withEntity(entity)
            .addCredentials(credentials)

          request ~> route ~> check {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldEqual "5e1f495e4e48bd44eba2550b"
          }
      }
    }

    "reject POST request with incorrect authentication" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val credentials = BasicHttpCredentials("TestNormalUser", "SomeWrongRandomPass")
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Post(s"/api/v1/competitions")
            .withEntity(entity)
            .addCredentials(credentials)

          request ~> route ~> check {
            status shouldBe StatusCodes.Unauthorized
            responseAs[String] shouldEqual "The supplied authentication is invalid"
          }
      }
    }

    "reject PUT request with incorrect authentication" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val credentials = BasicHttpCredentials("TestNormalUser", "SomeWrongRandomPass")
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Put(s"/api/v1/competitions")
            .withEntity(entity)
            .addCredentials(credentials)

          request ~> route ~> check {
            status shouldBe StatusCodes.Unauthorized
            responseAs[String] shouldEqual "The supplied authentication is invalid"
          }
      }
    }

    "reject POST request without authentication" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Post(s"/api/v1/competitions")
            .withEntity(entity)

          request ~> route ~> check {
            status shouldBe StatusCodes.Unauthorized
            responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
          }
      }
    }

    "reject PUT request without authentication" in {
      withTextFile("testdata/competition.json") {
        txt =>
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val request = Put(s"/api/v1/competitions")
            .withEntity(entity)

          request ~> route ~> check {
            status shouldBe StatusCodes.Unauthorized
            responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
          }
      }
    }

    "handle competitorsDataVersion request" - {

      "respond with current version to GET request without authentication" in {
        val request = Get(s"/api/v1/competitorsDataVersion/rifle")

        request ~> route ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldEqual "3"
        }
      }

      "reject any PUT request" in {
        withTextFile("testdata/competitorsData.json") {
          txt =>
            val credentials = BasicHttpCredentials("TestLvl2User", "SomeRandomPass")
            val entity = Marshal(txt).to[MessageEntity].futureValue

            val request = Put(s"/api/v1/competitorsDataVersion/rifle")
              .withEntity(entity)
              .addCredentials(credentials)

            request ~> route ~> check {
              rejections.length shouldBe 2
            }
        }
      }
    }

    "handle competitorsData request" - {

      "reject GET request without authentication" in {
        val request = Get(s"/api/v1/competitorsData/rifle")

        request ~> route ~> check {
          status shouldBe StatusCodes.Unauthorized
          responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
        }
      }

      "reject PUT request without authentication" in {
        withTextFile("testdata/competitorsData.json") {
          txt =>
            val entity = Marshal(txt).to[MessageEntity].futureValue
            val request = Put(s"/api/v1/competitorsData/rifle")
              .withEntity(entity)

            request ~> route ~> check {
              status shouldBe StatusCodes.Unauthorized
              responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
            }
        }
      }

      "reject authenticated GET request with insufficient access level" in {
        val credentials = BasicHttpCredentials("TestLvl0User", "SomeRandomPass")
        val request = Get(s"/api/v1/competitorsData/rifle")
          .addCredentials(credentials)

        request ~> route ~> check {
          status shouldBe StatusCodes.Forbidden
          responseAs[String] shouldEqual "Insufficient access rights"
        }
      }

      "reject authenticated PUT request with insufficient access level" in {
        withTextFile("testdata/competitorsData.json") {
          txt =>
            val credentials = BasicHttpCredentials("TestNormalUser", "SomeRandomPass")
            val entity = Marshal(txt).to[MessageEntity].futureValue
            val request = Put(s"/api/v1/competitorsData/rifle")
              .withEntity(entity)
              .addCredentials(credentials)

            request ~> route ~> check {
              status shouldBe StatusCodes.Forbidden
              responseAs[String] shouldEqual "Insufficient access rights"
            }
        }
      }

      "respond with competitors data to GET request with authentication" in {
        val credentials = BasicHttpCredentials("TestLvl2User", "SomeRandomPass")
        val request = Get(s"/api/v1/competitorsData/rifle")
          .addCredentials(credentials)


        request ~> route ~> check {
          status shouldBe StatusCodes.OK
          responseAs[String] shouldEqual "[{\"firstName\":\"Paul\",\"lastName\":\"Põld\",\"birthYear\":2001,\"club\":\"Mighty Ducks\",\"ordering\":0}]"
        }
      }

      "respond to authenticated PUT request and update the competitors data" in {
        withTextFile("testdata/competitorsData.json") {
          txt =>
            val credentials = BasicHttpCredentials("TestLvl2User", "SomeRandomPass")
            val entity = Marshal(txt).to[MessageEntity].futureValue
            val request = Put(s"/api/v1/competitorsData/rifle")
              .withEntity(entity)
              .addCredentials(credentials)

            request ~> route ~> check {
              status shouldBe StatusCodes.Created
              responseAs[String] shouldEqual "OK"
            }
        }
      }
    }
  }
}
