package ee.zone.web.protokollitaja.backend.persistence

import com.github.t3hnar.bcrypt._
import com.mongodb.client.result.UpdateResult
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.entities._
import ee.zone.web.protokollitaja.backend.metrics.Metrics
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala._
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.Updates._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

abstract class PersistenceBase {

//  private val userCodecRegistry = fromRegistries(fromProviders(classOf[User]), DEFAULT_CODEC_REGISTRY)
//  private val competitionCodecRegistry = fromRegistries(fromProviders(classOf[Competition], classOf[Event], classOf[Competitor], classOf[Series], classOf[Subtotals]), DEFAULT_CODEC_REGISTRY)
//  private val metricsCodecRegistry = fromRegistries(fromProviders(classOf[Metrics]), DEFAULT_CODEC_REGISTRY)
//  private val eventCodecRegistry = fromRegistries(fromProviders(classOf[Event]), DEFAULT_CODEC_REGISTRY)
//  private val competitorCodecRegistry = fromRegistries(fromProviders(classOf[Competitor]), DEFAULT_CODEC_REGISTRY)
//  private val seriesCodecRegistry = fromRegistries(fromProviders(classOf[Series]), DEFAULT_CODEC_REGISTRY)
//  private val subtotalsCodecRegistry = fromRegistries(fromProviders(classOf[Subtotals]), DEFAULT_CODEC_REGISTRY)

//  private val mongoClient = MongoClient(s"mongodb://${config.getString("db.addr")}:${config.getInt("db.port")}")
//  Thread.sleep(100)
//  private val database = mongoClient.getDatabase(config.getString("db.db_name"))

//  private val usersCollection: MongoCollection[User] = database
//    .withCodecRegistry(userCodecRegistry)
//    .getCollection("users")

//  private val competitionsCollection: MongoCollection[Competition] = database
//    .withCodecRegistry(competitionCodecRegistry)
//    .getCollection("competitions")

//  private val metricsCollection: MongoCollection[Metrics] = database
//    .withCodecRegistry(metricsCodecRegistry)
//    .getCollection("metrics")

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
