import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration._

class WebSocketWorkerSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  "The WSServerWorker" should {
    "respond to messages" in {
      //      val wsServer = new WebSocketServer
      val wsClient = WSProbe()

//      WS("/greeter", wsClient.flow) ~> WebSocketServer.route ~>
//        check {
//          isWebSocketUpgrade shouldBe true
//
//          wsClient.sendMessage("Lauri")
//          wsClient.expectMessage("Hello Lauri")
//
//          wsClient.sendCompletion()
//          wsClient.expectNoMessage(1.second)  // Depending on used flow, server does not respond with Completion
//        }
    }

    //    "respond wth bad request to requests for incorrect path" in {
    //      val wsServer = new WebSocketServer
    //      val wsClient = WSProbe()
    //
    //      WS("/hello", wsClient.flow) ~> wsServer.route ~>
    //        check {
    //          isWebSocketUpgrade shouldBe false

    //          wsClient.sendMessage("Hi!")
    //          wsClient.expectMessage("Hello world!")
    //
    //          wsClient.sendCompletion()
    //          wsClient.expectCompletion()
    //        }
    //    }
  }
}
