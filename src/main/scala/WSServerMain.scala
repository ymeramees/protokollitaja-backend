import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.pattern.ask
import scala.concurrent.duration._

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success}

object WSServerMain /*extends App*/ {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

//  val wsServer = WebSocketServer

  val host = "localhost"
  val port = 8080
  val wsServer = system.actorOf(Props[WebSocketServer])
  wsServer ! Start(host, port)

//  val bindingFuture = Http().bindAndHandle(wsServer.route, interface = host, port = port) onComplete {
//    case Success(value) => println(s"Websocket server waiting on ${value.localAddress}")
//    case Failure(exception) =>
//      println(s"Failed to start websocket server on $host:$port", exception)
//      Thread.sleep(10000)
//  }

  println(s"Press Return to stop...")
  StdIn.readLine()
  (wsServer ? Stop)(3.seconds) onComplete (_ => system.terminate())
  import system.dispatcher

  //  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())

}
