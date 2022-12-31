package ee.zone.web.protokollitaja.backend

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.DispatcherSelector
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, MessageEntity, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.sslconfig.ssl.{TrustManagerConfig, TrustStoreConfig}
import ee.zone.web.protokollitaja.backend.entities.{Competition, Competitor, Event, Team, User}
import ee.zone.web.protokollitaja.backend.persistence.Persistence
import ee.zone.web.protokollitaja.backend.server.ServerMain
import org.json4s._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.mongo.ObjectIdSerializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class ServerMainIntegrationTest extends AnyWordSpec with Matchers with BeforeAndAfterAll with WithResources {
  private val testKit = ActorTestKit()
  implicit val actorSystem = testKit.system.toClassic
  implicit val dispatcher = testKit.system.dispatchers.lookup(DispatcherSelector.default())

  val config = ConfigFactory.load()
  val persistence = new Persistence(config)

  private val server = ServerMain
  server.main(Array())

  // For some reason unable to enable self-signed certificates properly from application.conf, therefore doing it here
  val trustStoreConfig = TrustStoreConfig(None, Some("./src/test/resources/localhost.jks")).withStoreType("JKS")
  val trustManagerConfig = TrustManagerConfig().withTrustStoreConfigs(List(trustStoreConfig))

  val testSslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose
    .withAcceptAnyCertificate(true)
    .withDisableHostnameVerification(true)
  ).withTrustManagerConfig(trustManagerConfig))

  val testCtx = Http().createClientHttpsContext(testSslConfig)
  Http().setDefaultClientHttpsContext(testCtx)


  override def afterAll(): Unit = {
    persistence.cleanUpDatabase()
    testKit.shutdownTestKit()
  }

  "Server" should {
    "return a list of competitions" in {
      val response = Await.result(Http().singleRequest(HttpRequest(uri = "https://localhost:3005/api/v1/competitions")), 2.second)

      response.status shouldBe StatusCodes.OK
      Await.result(Unmarshal(response).to[String], 1.second) shouldBe "[]"
    }

    "return a competition id to new competition POST request and return same competition to next GET request" in {
      Await.result(persistence.saveUser(User("testuser_lvl1", "testuserPass", 1)), 2.second)
      withTextFile("testdata/competition.json") {
        txt =>
          val credentials = BasicHttpCredentials("testuser_lvl1", "testuserPass")
          val entity = Marshal(txt).to[MessageEntity].futureValue
          val response = Await.result(Http().singleRequest(
            HttpRequest(method = HttpMethods.POST, uri = "https://localhost:3005/api/v1/competitions")
              .withEntity(entity)
              .addCredentials(credentials)
          ), 2.second)

          response.status shouldBe StatusCodes.Created
          Await.result(Unmarshal(response).to[String], 1.second) shouldBe "5e1f495e4e48bd44eba2550b"
      }

      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer

          val competition = json.extract[Competition]
          val response = Await.result(
            Http().singleRequest(HttpRequest(uri = "https://localhost:3005/api/v1/competitions/5e1f495e4e48bd44eba2550b/7")),
            2.second
          )

          response.status shouldBe StatusCodes.OK
          val str = Await.result(Unmarshal(response).to[String], 1.second)
          val json2 = parse(str).transformField { // Hack to serialize _id to ObjectId
            case ("id", s: JValue) =>
              val idString = s.extract[String]
              if (idString.startsWith("5e") && idString.length == 24) {
                ("_id", parse("{\"$oid\":\"" + s.extract[String] + "\"}"))
              } else {
                ("_id", s)
              }
          }
          val receivedResults = json2.extract[Event]
          val expectedCompetitors = competition.events.filter(_._id == "7").head.competitors.map(_.copy(birthYear = ""))
          receivedResults.competitors shouldEqual expectedCompetitors
      }
      persistence.cleanUpDatabase()
    }

    "return an event from an existing competition to a GET request" in {
      withCompetitionJsonFile("testdata/competition.json") {
        json =>
          implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer

          val competition = json.extract[Competition]
          Await.result(persistence.saveCompetition(competition), 1.second)

          val response = Await.result(Http().singleRequest(HttpRequest(uri = "https://localhost:3005/api/v1/competitions/5e1f495e4e48bd44eba2550b/7")), 2.second)

          response.status shouldBe StatusCodes.OK
          val str = Await.result(Unmarshal(response).to[String], 1.second)
          val json2 = parse(str).transformField { // Hack to serialize _id to ObjectId
            case ("id", s: JValue) =>
              val idString = s.extract[String]
              if (idString.startsWith("5e") && idString.length == 24) {
                ("_id", parse("{\"$oid\":\"" + s.extract[String] + "\"}"))
              } else {
                ("_id", s)
              }
          }
          val receivedResults = json2.extract[Event]
          val expectedCompetitors = competition.events.filter(_._id == "7").head.competitors.map(_.copy(birthYear = ""))
          val expectedTeams = competition.events.filter(_._id == "7").head.teams
          receivedResults.competitors shouldEqual expectedCompetitors
          receivedResults.teams shouldEqual expectedTeams
      }
      persistence.cleanUpDatabase()
    }
  }
}
