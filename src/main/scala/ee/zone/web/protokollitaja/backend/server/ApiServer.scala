package ee.zone.web.protokollitaja.backend.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, Scheduler}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, RequestContext, Route, RouteResult}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.auth.Authenticator
import ee.zone.web.protokollitaja.backend.entities.{Competition, DBCompetitor}
import ee.zone.web.protokollitaja.backend.persistence.PersistenceBase
import ee.zone.web.protokollitaja.backend.protocol.BackendProtocol.{BackendMsg, GetRoute, SendRoute}
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.json4s.mongo.ObjectIdSerializer
import org.json4s.{DefaultFormats, Formats, JValue, MappingException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

object ApiServer {
  def apply(persistence: PersistenceBase, parserDispatcher: ExecutionContext, config: Config): Behavior[BackendMsg] = {
    Behaviors.setup { context =>
      implicit val materializer: Materializer = Materializer(context)
      implicit val scheduler: Scheduler = context.system.scheduler
      new ApiServer(context, persistence, config)(parserDispatcher)
    }
  }
}

class ApiServer(context: ActorContext[BackendMsg], persistence: PersistenceBase, config: Config)(implicit parserDispatcher: ExecutionContext)
  extends AbstractBehavior[BackendMsg](context) with Directives with LazyLogging with CorsHandler {

  implicit val materializer: Materializer = Materializer(context)
  implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer

  private val MAX_COMPETITION_PAYLOAD = config.getLong("server.max_competition_payload")

  override def onMessage(msg: BackendMsg): Behavior[BackendMsg] = msg match {
    case GetRoute(from) =>
      println(s"Route: ${route}")
      from ! SendRoute(route)
      this

    case _ =>
      logger.error(s"An unknown message was received: $msg")
      this
  }

  private val competitions =
    ignoreTrailingSlash {
      path("competitions") {
        pathEnd {
          get { // GET is without authentication
            onSuccess(persistence.getCompetitionHeaders) { headers =>
              val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, write(headers)))
              complete(response)
            }
          } ~ Route.seal { // PUT and POST require authentication
            put {
              Authenticator.bcryptAuthAsync("secure site", persistence, Authenticator.bcryptAuthenticator) { userNameAndLevel =>
                val (username, accessLevel) = userNameAndLevel
                if (accessLevel > 0) {
                  extractRequestEntity { entity =>
                    requestContext =>
                      extractData(entity.withSizeLimit(MAX_COMPETITION_PAYLOAD), requestContext).flatMap {
                        case Some(competitionJson) =>
                          handleCompetitionUpdate(competitionJson, requestContext)
                        case _ =>
                          logger.error("Unable to extract data")
                          requestContext.complete(StatusCodes.BadRequest -> "Unable to extract data")
                      }
                  }
                } else {
                  val responseText = s"Insufficient access rights for $username"
                  logger.error(responseText)
                  complete(StatusCodes.Forbidden -> responseText)
                }
              }
            } ~ post {
              Authenticator.bcryptAuthAsync("secure site", persistence, Authenticator.bcryptAuthenticator) { userNameAndLevel =>
                val (username, accessLevel) = userNameAndLevel
                if (accessLevel > 0) {
                  extractRequestEntity { entity =>
                    requestContext =>
                      extractData(entity.withSizeLimit(MAX_COMPETITION_PAYLOAD), requestContext).flatMap {
                        case Some(competitionJson) =>
                          try {
                            handleCompetitionSave(competitionJson, requestContext)
                          } catch {
                            case e: MappingException =>
                              logger.error(s"handleCompetitionSave failed: ${e.getMessage}\nfor json: $competitionJson")
                              requestContext.complete(StatusCodes.BadRequest -> e.getMessage)
                          }
                        case _ =>
                          logger.error("Unable to extract data")
                          requestContext.complete(StatusCodes.BadRequest -> "Unable to extract data")
                      }
                  }
                } else {
                  val responseText = s"Insufficient access rights for $username"
                  logger.error(responseText)
                  complete(StatusCodes.Forbidden -> responseText)
                }
              }
            }
          }
        }
      }
    }

  private val events =
    get {
      path("competitions" / Segment) { competitionId =>
        pathEndOrSingleSlash {
          onSuccess(persistence.getEventHeaders(competitionId)) { eventHeaders =>
            val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, write(eventHeaders)))
            complete(response)
          }
        }
      }
    }

  private val results =
    get {
      path("competitions" / Segments(2)) { idsList =>
        pathEndOrSingleSlash {
          if (idsList.length == 2) {
            onSuccess(persistence.getEventResults(idsList.head, idsList.last)) { e =>
              val event = write(e.copy(competitors = e.competitors.map(_.copy(birthYear = ""))))
                .replace("_id", "id")
              persistence.getEventsLoadCount.onComplete {
                case Success(value) =>
                  logger.info(s"Number of times competitors have been asked: $value")
                case _ =>
                  logger.error("Unable to get a number how many times competitors have been asked!")
              }
              val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, event))
              complete(response)
            }
          } else {
            logger.error("idsList.length != 2")
            complete(StatusCodes.BadRequest)
          }
        }
      }
    }

  private val competitorsDataVersion =
    get {
      path("competitorsDataVersion" / Segment) { listName =>
        pathEndOrSingleSlash {
          onSuccess(persistence.getCompetitorsDataVersion(listName)) { v =>
            logger.info(s"Competitors data version asked for $listName, responding with $v")
            complete(StatusCodes.OK -> v.toString)
          }
        }
      }
    }

  private val competitorsData =
    ignoreTrailingSlash {
      path("competitorsData" / Segment) { listName =>
        Route.seal { // require authentication
          Authenticator.bcryptAuthAsync("secure site", persistence, Authenticator.bcryptAuthenticator) { userNameAndLevel =>
            val (_, accessLevel) = userNameAndLevel
            get {
              if (accessLevel > 0) {
                onSuccess(persistence.getCompetitorsData(listName)) { data =>
                  val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, write(data)))
                  complete(response)
                }
              } else {
                complete(StatusCodes.Forbidden -> "Insufficient access rights")
              }
            } ~ put {
              if (accessLevel >= 2) {
                extractRequestEntity { entity =>
                  requestContext =>
                    extractData(entity.withSizeLimit(MAX_COMPETITION_PAYLOAD), requestContext).flatMap {
                      case Some(competitorsDataJson) =>
                        handleCompetitorsDataSave(listName, competitorsDataJson, requestContext)
                      case _ =>
                        logger.error("Unable to extract data")
                        requestContext.complete(StatusCodes.BadRequest -> "Unable to extract data")
                    }
                }
              } else {
                logger.error(s"Insufficient access rights for ${userNameAndLevel._1}")
                complete(StatusCodes.Forbidden -> "Insufficient access rights")
              }
            }
          }
        }
      }
    }

  val route: Route =
    corsHandler(
      pathPrefix("api") {
        pathPrefix("v1") {
          competitions ~ events ~ results ~ competitorsDataVersion ~ competitorsData
        }
      }
    )

  private def extractData(entity: RequestEntity, requestContext: RequestContext): Future[Option[JValue]] = {
    val dataFuture = entity.httpEntity.dataBytes.runWith(Sink.fold[String, ByteString]("")((text, bs) => {
      (text.appendedAll(bs.utf8String))
    }))
    try {
      dataFuture.map { dataString =>
        Some(parse(dataString).transformField { // Hack to serialize id to ObjectId
          case ("id", s: JValue) =>
            val idString = s.extract[String]
            val competitionIdRegex = "^(?:5|6|7)(?=.*?\\d)(?=.*?[a-zA-Z])[a-zA-Z\\d]+$".r // Start with 5, 6, or 7, at least 1 letter and 1 digit with only letters and digits
            idString match {
              case competitionIdRegex(_*) =>
                ("_id", parse("{\"$oid\":\"" + idString + "\"}"))
              case _ =>
                ("_id", s)
            }
        })
      }
    } catch {
      case exception: Exception =>
        logger.error(s"DataFuture failed with an exception: $exception")
        Future.failed(exception)
    }
  }

  private def handleCompetitionUpdate(json: JValue, requestContext: RequestContext): Future[RouteResult] = {
    val competition = json.extract[Competition]
    persistence.updateCompetition(competition).map { _ =>
      requestContext.complete(StatusCodes.OK -> s"${competition._id.toString}")
    } recover {
      case e: RuntimeException =>
        logger.error(s"handleCompetitionUpdate failed: ${e.getMessage}")
        requestContext.complete(StatusCodes.BadRequest -> e.getMessage)
    }
  }.flatten


  private def handleCompetitionSave(json: JValue, requestContext: RequestContext): Future[RouteResult] = {
    val competition = json.extract[Competition]
    persistence.saveCompetition(competition).map { _ =>
      requestContext.complete(StatusCodes.Created -> s"${competition._id.toString}")
    } recover {
      case e: RuntimeException =>
        logger.error(s"handleCompetitionSave failed: ${e.getMessage}")
        requestContext.complete(StatusCodes.BadRequest -> e.getMessage)
    }
  }.flatten

  private def handleCompetitorsDataSave(listName: String, json: JValue, requestContext: RequestContext): Future[RouteResult] = {
    val competitorsDataList = json.extract[Seq[DBCompetitor]]
    persistence.saveCompetitorsData(listName, competitorsDataList).map { _ =>
      requestContext.complete(StatusCodes.Created -> "OK")
    } recover {
      case e: RuntimeException =>
        logger.error(s"handleCompetitorsDataSave failed: ${e.getMessage}")
        requestContext.complete(StatusCodes.BadRequest -> e.getMessage)
    }
  }.flatten
}
