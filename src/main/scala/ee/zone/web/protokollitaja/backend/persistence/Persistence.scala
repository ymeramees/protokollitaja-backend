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

class Persistence(config: Config) extends PersistenceBase with LazyLogging {

  private val userCodecRegistry = fromRegistries(fromProviders(classOf[User]), DEFAULT_CODEC_REGISTRY)
  private val competitionCodecRegistry = fromRegistries(fromProviders(classOf[Competition], classOf[Event], classOf[Competitor], classOf[Series], classOf[Subtotals]), DEFAULT_CODEC_REGISTRY)
  private val metricsCodecRegistry = fromRegistries(fromProviders(classOf[Metrics]), DEFAULT_CODEC_REGISTRY)

  private val mongoClient = MongoClient(s"mongodb://${config.getString("db.addr")}:${config.getInt("db.port")}")
  Thread.sleep(100)
  private val database = mongoClient.getDatabase(config.getString("db.db_name"))

  private val usersCollection: MongoCollection[User] = database
    .withCodecRegistry(userCodecRegistry)
    .getCollection("users")

  private val competitionsCollection: MongoCollection[Competition] = database
    .withCodecRegistry(competitionCodecRegistry)
    .getCollection("competitions")

  private val metricsCollection: MongoCollection[Metrics] = database
    .withCodecRegistry(metricsCodecRegistry)
    .getCollection("metrics")

  def getCompetition(competitionId: String): Future[Option[Competition]] = {
    competitionsCollection.find(equal("_id", new ObjectId(competitionId))).headOption
  }

  def getCompetition(competitionId: ObjectId): Future[Option[Competition]] = {
    competitionsCollection.find(equal("_id", competitionId)).headOption
  }

  def getCompetitionByName(competitionName: String): Future[Option[Competition]] = {
    competitionsCollection.find(equal("competitionName", competitionName)).headOption
  }

  def getCompetitionHeaders: Seq[CompetitionHeader] = {
    println("Reading competition headers")
    val competitions = Await.result(competitionsCollection.find().sort(orderBy(descending("_id"))).toFuture(), 500.millis)
    logger.debug(s"Number of competitions found: ${competitions.length}")
    competitions.map(c => CompetitionHeader(c._id.toString, c.competitionName, c.timeAndPlace))
  }

  def getEventHeaders(competitionId: String): Seq[EventHeader] = {
    val competition = Await.result(getCompetition(competitionId), 500.millis)
    competition match {

      case Some(competition) =>
        logger.debug(s"Number of events found in ${competition.competitionName}: ${competition.events.length}")
        competition.events.map(event => EventHeader(event._id.toString, event.eventName))

      case _ =>
        logger.warn(s"getEventHeaders: competitionId $competitionId not found!")
        Seq()
    }
  }

  def getEventCompetitors(competitionId: String, eventId: String): Seq[Competitor] = {
    val competition = Await.result(getCompetition(competitionId), 500.millis)
    competition match {

      case Some(competition) =>
        eventsLoadCountPlusOne()
        competition.events.find(event => event._id.toString == eventId) match {

          case Some(e) =>
            logger.debug(s"Number of competitiors found in ${competition.competitionName} in ${e.eventName}: ${e.competitors.length}")
            e.competitors

          case _ =>
            logger.warn(s"EventId $eventId not found in ${competition.competitionName}!")
            Seq()
        }

      case _ =>
        logger.warn(s"getEent: competitionId $competitionId not found!")
        Seq()
    }
  }

  def getEventsLoadCount: Int = {
    Await.result(metricsCollection.find().headOption(), 500.millis) match {

      case Some(metrics) =>
        metrics.eventsLoadCount

      case _ => // Events not requested yet and count not initialized, initialize
        Await.result(metricsCollection.insertOne(Metrics(0)).toFuture(), 500.millis)
        0
    }
  }

  def eventsLoadCountPlusOne(): Unit = {
    Await.result(
      metricsCollection.updateOne(exists("eventsLoadCount"), inc("eventsLoadCount", 1)).toFuture(),
      500.millis)
  }

  def changeUserPassword(username: String, oldPassword: String, newPassword: String): Future[UpdateResult] = {
    // If same username exists and old password is correct, save the new password
    Await.result(getPasswordAndAccessLevel(username), 50.millis) match {
      case Some(password) =>
        if (oldPassword.isBcryptedSafe(password._1).getOrElse(false)) {
          createBcryptedPassword(newPassword) match {

            case Success(hashedPassword) =>
              logger.info(s"Changing password for $username.")
              usersCollection.updateOne(equal("username", username), set("password", hashedPassword)).toFuture()

            case Failure(exception) =>
              Future.failed(exception)
          }
        } else {
          logger.warn(s"Wrong password was provided for changing $username's password!")
          Future.failed(new RuntimeException("Username and/or old password do not match!"))
        }

      case _ =>
        logger.warn(s"User $username not found!")
        Future.failed(new RuntimeException("Username and/or old password do not match!"))
    }
  }

  private def createBcryptedPassword(password: String): Try[String] = {
    password.bcrypt(12) match {

      case hashedPassword: String =>
        if (password.isBcryptedSafe(hashedPassword).getOrElse(false)) {
          Success(hashedPassword)
        } else {
          logger.error(s"Error when checking the new hashed password!")
          Failure(new RuntimeException("Hashing of the new password went wrong, try again!"))
        }

      case _ =>
        logger.error(s"Hashing of the password failed!")
        Failure(new RuntimeException("Hashing of the new password went wrong, try again!"))
    }
  }

  def saveCompetition(newCompetition: Competition): Future[Completed] = {
    val maybeExistingCompetition = Await.result(getCompetition(newCompetition._id), 50.millis)
    maybeExistingCompetition match {

      case Some(_) =>
        val responseString = s"Competition with id ${newCompetition._id} already existing! Use PUT request to update an existing competition!"
        logger.warn(responseString)
        Future.failed(new RuntimeException(responseString))

      case _ =>
        val maybeExistingName = Await.result(getCompetitionByName(newCompetition.competitionName), 50.millis)
        maybeExistingName match {

          case Some(competition) =>
            val responseString = s"Competition with same name already existing, id: ${competition._id}!"
            logger.warn(responseString)
            Future.failed(new RuntimeException(responseString))

          case _ =>
            logger.info(s"Saving competition: ${newCompetition.competitionName}, id:${newCompetition._id} ")
            competitionsCollection.insertOne(newCompetition).toFuture()
        }
    }
  }

  def updateCompetition(newCompetition: Competition): Future[Competition] = {
    val maybeExistingCompetition = Await.result(getCompetition(newCompetition._id), 50.millis)
    maybeExistingCompetition match {

      case Some(_) =>
        logger.info(s"Updating competition: ${newCompetition.competitionName}, id:${newCompetition._id} ")
        competitionsCollection.findOneAndReplace(equal("_id", newCompetition._id), newCompetition).toFuture()

      case _ =>
        val responseString = s"Competition with id ${newCompetition._id} not existing! Use POST request to save new competition!"
        logger.warn(responseString)
        Future.failed(new RuntimeException(responseString))
    }
  }

  def saveUser(newUser: User): Future[Completed] = {
    // If same username exists, do not save
    if (Await.result(usersCollection.find(equal("username", newUser.username)).toFuture(), 500.millis).isEmpty) {
      createBcryptedPassword(newUser.password) match {

        case Success(hashedPassword) =>
          logger.info(s"New user saved: ${newUser._id} - ${newUser.username}")
          usersCollection.insertOne(newUser.copy(password = hashedPassword)).toFuture()

        case Failure(exception) =>
          Future.failed(exception)
      }
    } else {
      logger.warn(s"User ${newUser.username} already exists!")
      Future.failed(new RuntimeException("User with this username already exists!"))
    }
  }

  def getPasswordAndAccessLevel(username: String): Future[Option[(String, Int)]] = {
    usersCollection.find(equal("username", username)).map(u => (u.password, u.accessLevel)).headOption()
  }

  def getAllUsers: Future[Seq[User]] = {
    usersCollection.find().sort(ascending("username")).toFuture()
  }

  //  def getUser(username: String): Future[Option[User]] = {
  //    usersCollection.find(equal("username", username)).headOption()
  //  }

  def cleanUpDatabase(): Completed = { // Used in unit tests
    Await.result(database.drop().toFuture(), 1.second)
  }
}
