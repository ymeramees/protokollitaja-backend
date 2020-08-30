package ee.zone.web.protokollitaja.backend.server

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}

import akka.actor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector, Terminated}
import akka.http.scaladsl.{ConnectionContext, Http}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.persistence.Persistence
import ee.zone.web.protokollitaja.backend.protocol.BackendProtocol.{BackendMsg, GetRoute, SendRoute}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object ServerMain extends LazyLogging {
  def apply(): Behavior[BackendMsg] =
    Behaviors.setup { context =>
      val config = ConfigFactory.load()
      val persistence = new Persistence(config)
      val parserDispatcher = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("parser-dispatcher"))
      val apiServer = context.spawn(ApiServer(persistence, parserDispatcher, config), "apiServer")
      context.watch(apiServer)

      implicit val actorSystem: actor.ActorSystem = context.system.toClassic

      implicit val executionContext: ExecutionContextExecutor = context.executionContext
      val host = config.getString("server.addr")
      val port = config.getInt("server.port")

      val password = config.getString("server.cert_password").toCharArray
      val certFile = config.getString("server.cert_file")

      if (password.isEmpty)
        logger.warn("Cert password is empty!")

      val file = Paths.get(certFile)
      logger.info(s"Cert file absolute path: ${file.toAbsolutePath}")

      val ks = KeyStore.getInstance("PKCS12")
      val keyStore = Files.newInputStream(Paths.get(certFile))

      require(keyStore != null, "Keystore required!")
      ks.load(keyStore, password)

      val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(ks, password)

      val tmf = TrustManagerFactory.getInstance("SunX509")
      tmf.init(ks)

      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
      val https = ConnectionContext.https(sslContext)
      Http().setDefaultServerHttpContext(https)

      apiServer ! GetRoute(context.self)

      Behaviors.receive { (context, message) =>
        message match {
          case SendRoute(route) =>
            Http().bindAndHandle(route, host, port) onComplete {
              case Success(value) => println(s"Protokollitaja API server waiting on ${
                value.localAddress
              }")
              case Failure(exception) =>
                println(s"Failed to start Protokollitaja API server on $host:$port", exception)
                logger.error(s"Failed to start Protokollitaja API server on $host:$port", exception)
                Thread.sleep(10000)
                Runtime.getRuntime.halt(140)
            }

            Behaviors.receiveSignal {
              case (_, Terminated(_)) =>
                Behaviors.stopped
            }

          case _ => Behaviors.same // Ignore other messages
        }
      }
    }

  def main(args: Array[String]): Unit = {
    ActorSystem(ServerMain(), "serverMain")
  }
}
