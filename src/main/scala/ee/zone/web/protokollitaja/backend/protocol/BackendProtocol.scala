package ee.zone.web.protokollitaja.backend.protocol

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route

object BackendProtocol {

  sealed trait BackendMsg

  final case class GetRoute(from: ActorRef[BackendMsg]) extends BackendMsg

  final case class SendRoute(route: Route) extends BackendMsg

  case class Start(system: ActorSystem[BackendMsg]) extends BackendMsg

}
