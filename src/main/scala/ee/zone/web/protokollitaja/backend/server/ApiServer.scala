package ee.zone.web.protokollitaja.backend.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, Scheduler}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, RequestContext, Route, RouteResult}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.auth.Authenticator
import ee.zone.web.protokollitaja.backend.entities.Competition
import ee.zone.web.protokollitaja.backend.persistence.PersistenceBase
import ee.zone.web.protokollitaja.backend.protocol.BackendProtocol.{BackendMsg, GetRoute, SendRoute}
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.json4s.mongo.ObjectIdSerializer
import org.json4s.{DefaultFormats, Formats, JValue}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object ApiServer {
  def apply(persistence: PersistenceBase, parserDispatcher: ExecutionContext): Behavior[BackendMsg] = {
    Behaviors.setup { context =>
      implicit val materializer: Materializer = Materializer(context)
      implicit val scheduler: Scheduler = context.system.scheduler
      new ApiServer(context, persistence)(parserDispatcher)
    }
  }
}

class ApiServer(context: ActorContext[BackendMsg], persistence: PersistenceBase)(implicit parserDispatcher: ExecutionContext)
  extends AbstractBehavior[BackendMsg](context) with Directives with LazyLogging with CorsHandler {

  implicit val materializer: Materializer = Materializer(context)
  implicit val formats: Formats = DefaultFormats + new ObjectIdSerializer

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
            val competitionHeaders = write(persistence.getCompetitionHeaders)
            val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, competitionHeaders))
            complete(response)
          } ~ Route.seal { // PUT and POST require authentication
            put {
              Authenticator.bcryptAuthAsync("secure site", persistence, Authenticator.bcryptAuthenticator) { userNameAndLevel =>
                val (username, accessLevel) = userNameAndLevel
                if (accessLevel > 0) {
                  extractRequestEntity { entity =>
                    requestContext =>
                      extractData(entity, requestContext).flatMap {
                        case Some(competitionJson) =>
                          handleCompetitionUpdate(competitionJson, requestContext)
                        case _ =>
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
                      extractData(entity, requestContext).flatMap {
                        case Some(competitionJson) =>
                          handleCompetitionSave(competitionJson, requestContext)

                        case _ =>
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
          val eventHeaders = write(persistence.getEventHeaders(competitionId))
          val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, eventHeaders))
          complete(response)
        }
      }
    }

  private val results =
    get {
      path("competitions" / Segments(2)) { idsList =>
        pathEndOrSingleSlash {
          if (idsList.length == 2) {
            val event = write(
              persistence.getEventCompetitors(idsList.head, idsList.last)
                .map(_.copy(birthYear = ""))
            ).replace("_id", "id")
            logger.info(s"Number of times competitors have been asked: ${persistence.getEventsLoadCount}")
            val response = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, event))
            complete(response)
          } else {
            complete(StatusCodes.BadRequest)
          }
        }
      }
    }

  val route: Route =
    corsHandler(
      pathPrefix("api") {
        pathPrefix("v1") {
          competitions ~ events ~ results
        }
      }
    )

  private def extractData(entity: RequestEntity, requestContext: RequestContext): Future[Option[JValue]] = {
    val dataFuture = entity.httpEntity.dataBytes.runWith(Sink.fold[String, ByteString]("")((text, bs) => {
      (text.appendedAll(bs.utf8String))
    }))
    try {
      dataFuture.flatMap { dataString =>
        val json = parse(dataString).transformField { // Hack to serialize id to ObjectId
          case ("id", s: JValue) =>
            val idString = s.extract[String]
            if (idString.startsWith("5e") && idString.length == 24) {
              ("_id", parse("{\"$oid\":\"" + s.extract[String] + "\"}"))
            } else {
              ("_id", s)
            }
        }
        Future(Some(json))
      }

    } catch {
      case exception: Exception =>
        logger.error(s"DataFuture failed with an exception: $exception")
        Future(None)
    }
  }

  private def handleCompetitionUpdate(json: JValue, requestContext: RequestContext): Future[RouteResult] = {
    val competition = json.extract[Competition]
    persistence.updateCompetition(competition).flatMap { _ =>
      requestContext.complete(StatusCodes.OK -> s"${competition._id.toString}")
    } recover {
      case e: RuntimeException =>
        Await.result(requestContext.complete(StatusCodes.BadRequest -> e.getMessage), 1.second)
    }
  }


  private def handleCompetitionSave(json: JValue, requestContext: RequestContext): Future[RouteResult] = {
    val competition = json.extract[Competition]
    persistence.saveCompetition(competition).flatMap { _ =>
      requestContext.complete(StatusCodes.Created -> s"${competition._id.toString}")
    } recover {
      case e: RuntimeException =>
        Await.result(requestContext.complete(StatusCodes.BadRequest -> e.getMessage), 1.second)
    }
  }
}
