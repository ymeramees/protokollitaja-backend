package ee.zone.web.protokollitaja.backend.server

import akka.actor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector, Terminated}
import akka.http.scaladsl.{ConnectionContext, Http}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.persistence.Persistence
import ee.zone.web.protokollitaja.backend.protocol.BackendProtocol.{BackendMsg, GetRoute, SendRoute}

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object ServerMain extends LazyLogging {
  def apply(): Behavior[BackendMsg] =
    Behaviors.setup { context =>
      val config = ConfigFactory.load()

      implicit val executionContext: ExecutionContextExecutor = context.executionContext

      val persistence = new Persistence(config)
      val parserDispatcher = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("parser-dispatcher"))
      val apiServer = context.spawn(ApiServer(persistence, parserDispatcher, config), "apiServer")
      context.watch(apiServer)

      implicit val actorSystem: actor.ActorSystem = context.system.toClassic

      val host = config.getString("server.addr")
      val httpsPort = config.getInt("server.httpsPort")
      val httpPort = config.getInt("server.httpPort")

      val password = config.getString("server.cert_password").toCharArray
      val certFile = config.getString("server.cert_file")

      if (password.length <= 0)
        logger.warn("Cert password is empty!")

      val file = Paths.get(certFile)

      apiServer ! GetRoute(context.self)

      Behaviors.receive { (context, message) =>
        message match {
          case SendRoute(route) => {
            if (Files.exists(file)) {
              logger.info(s"Cert file absolute path: ${file.toAbsolutePath}")

              val ks = KeyStore.getInstance("PKCS12")
              val keyStore = Files.newInputStream(Paths.get(certFile))

              if (keyStore != null) {
                ks.load(keyStore, password)

                val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
                keyManagerFactory.init(ks, password)

                val tmf = TrustManagerFactory.getInstance("SunX509")
                tmf.init(ks)

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
                val https = ConnectionContext.https(sslContext)
                Http().bindAndHandle(route, host, httpsPort, connectionContext = https)
              } else Future.successful(())
            }
            else {
              logger.warn(s"Cert file ${file.toAbsolutePath} not found, so no HTTPS!")
              Future.successful(())
            }
          }
            .map(_ =>
              Http().bindAndHandle(route, host, httpPort) onComplete {
                case Success(value) => println(s"Protokollitaja API server waiting on ${
                  value.localAddress
                }")
                case Failure(exception) =>
                  println(s"Failed to start Protokollitaja API server on $host:$httpsPort", exception)
                  logger.error(s"Failed to start Protokollitaja API server on $host:$httpsPort", exception)
                  Thread.sleep(10000)
                  Runtime.getRuntime.halt(140)
              })

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
