package ee.zone.web.protokollitaja.backend.playground

import akka.actor.Actor
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class WebSocketWorker extends Actor {
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  val (down, wsPublisher) = Source.actorRef[String](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()


  override def receive: Receive = {
    case GetWebSocketFlow =>
      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._
        val textMsgFlow = b.add(Flow[Message].mapAsync(1) {
          case tm: TextMessage => tm.toStrict(3.seconds).map(_.text)
          case bm: BinaryMessage =>
            bm.dataStream.runWith(Sink.ignore)
            Future.failed(new Exception("Yuck!"))
        })

        val pubSrc = b.add(Source.fromPublisher(wsPublisher).map(TextMessage(_)))

        textMsgFlow ~> Sink.foreach[String](self ! _)
        FlowShape(textMsgFlow.in, pubSrc.out)
      })

      sender ! flow

    case s: String =>
      println(s"Received text: $s, forwarding.")
      down ! "Hello " + s

    case n: Int =>
      println(s"Received int: $n, forwarding.")
      down ! n.toString

//    case msg =>
//      println(s"Received a message: $msg")
  }
}
