import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import akka.stream.{ActorMaterializer, SourceShape}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class WebSocketSingleServer extends Actor { // extends Actor with Directives {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

    val greeterWebSocketService = {
      Flow[Message].mapConcat {
        case textMsg: TextMessage =>
          TextMessage(Source.single("Hello ") ++ textMsg.textStream) :: Nil
        case binaryMsg: BinaryMessage =>
          binaryMsg.dataStream.runWith(Sink.ignore)
          Nil
      }
    }

    val helloSource = Source(List(
      TextMessage("Hello hello!"),
      TextMessage("Hello hello!"),
      TextMessage("Hello hello!"),
      TextMessage("Hello hello!")
    )).throttle(1, 1.second)

    val helloGraph = GraphDSL.create() { implicit builder =>

      val generator = builder.add(helloSource)

      SourceShape(generator.out)
    }

    val simpleSink = Sink.foreach[Message](println)

    val requestHandler: HttpRequest => HttpResponse = {
      case req@HttpRequest(GET, Uri.Path("/greeter"), _, _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
          case None => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case req@HttpRequest(GET, Uri.Path("/hello"), _, _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) => upgrade.handleMessagesWithSinkSource(simpleSink, helloGraph)
          case None => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes()
        HttpResponse(404, entity = "Unknown resource!")
    }
  var lastHandler: Option[ActorRef] = None

  override def receive: Receive = {
    case Start(host, port) =>
      val route: Route = path("greeter") {
        get {
          //      handleWith(requestHandler)
          val handler = context.actorOf(Props[WebSocketWorker])
          val futureFlow = (handler ? GetWebSocketFlow) (3.seconds).mapTo[Flow[Message, Message, _]]

          lastHandler = Some(handler)

          onComplete(futureFlow) {
            case Success(flow) =>
              println(s"Lauri: No of children: ${context.children.size}")
              handleWebSocketMessages(flow)
            case Failure(exception) => complete(exception.toString)
          }
          //      handleWebSocketMessages(greeterWebSocketService)
        }
      }

      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      val bindingFuture = Http().bindAndHandle(route, interface = host, port = port) onComplete {
        case Success(value) => println(s"Websocket server waiting on ${value.localAddress}")
        case Failure(exception) =>
          println(s"Failed to start websocket server on $host:$port", exception)
      }

    case Stop =>
      println("Received stop command")
      lastHandler.foreach(worker => context stop worker)
//      context.children.foreach(worker => context stop worker)
      context stop self

    case s: String =>
      println(s"Server received text: $s, forwarding.")
      lastHandler.map(worker => worker ! s)
    //      context.children.foreach(worker => worker ! s)

    case i: Int =>
      println(s"Server received int: $i, forwarding.")
      lastHandler.map(worker => worker ! i)
    //      context.children.foreach(worker => worker ! i)
  }

}

//case class GetWebSocketFlow()

//case class Start(host: String, port: Int)

//case class Stop()
