package ee.zone.web.protokollitaja.backend.playground

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class WebSocketServerSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {

  val wsServer = system.actorOf(Props[WebSocketSingleServer])
  wsServer ! Start("localhost", 8000)

  "The WSServerMain" should {
    "respond to messages" in {
      //      val wsServer = new ee.zone.web.protokollitaja.backend.playground.WebSocketServer
      val wsClient = WSProbe()

      val (upgradeResp, closed) = Http().singleWebSocketRequest(WebSocketRequest(s"ws://localhost:8000/greeter"), wsClient.flow)

      upgradeResp.map { upgrade =>
        upgrade.response.status shouldBe StatusCodes.SwitchingProtocols
      }

      Thread.sleep(500)

//      wsServer ! 565
      wsServer ! "565"
      wsClient.expectMessage("Hello 565")

      wsClient.sendMessage("Some name")
      wsClient.expectMessage("Hello Some name")

      //      WS("/greeter", wsClient.flow) ~> ee.zone.web.protokollitaja.backend.playground.WebSocketServer.route ~>
      //        check {
      //          isWebSocketUpgrade shouldBe true
      //
      //          wsClient.sendMessage("Hi!")
      //          wsClient.expectMessage("Hello world!")
      //
      wsClient.sendCompletion()

      wsClient.sendMessage("Peeter")
      wsClient.expectNoMessage(1.second)
//      wsClient.expectCompletion()
      //        }
    }

    //    "respond wth bad request to requests for incorrect path" in {
    //      val wsServer = new ee.zone.web.protokollitaja.backend.playground.WebSocketServer
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
