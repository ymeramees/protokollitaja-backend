package ee.zone.web.protokollitaja.backend.auth

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsMissing, CredentialsRejected}
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Directives}
import com.github.t3hnar.bcrypt._
import com.typesafe.scalalogging.LazyLogging
import ee.zone.web.protokollitaja.backend.persistence.PersistenceBase

import scala.concurrent.{ExecutionContext, Future}

object Authenticator extends Directives with LazyLogging {

  def bcryptAuthAsync[T](realm: String, persistence: PersistenceBase, authenticate: (String, String, PersistenceBase) => Future[Option[T]]): Directive1[T] = {
    def challenge = HttpChallenges.basic(realm)

    extractCredentials.flatMap {
      case Some(BasicHttpCredentials(username, password)) =>
        onSuccess(authenticate(username, password, persistence)).flatMap {
          case Some(usernameAndLevel) => provide(usernameAndLevel)
          case None => reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
        }
      case _ => reject(AuthenticationFailedRejection(CredentialsMissing, challenge))
    }
  }

  def bcryptAuthenticator(username: String, password: String, persistence: PersistenceBase)
                         (implicit ec: ExecutionContext): Future[Option[(String, Int)]] = {
    persistence.getPasswordAndAccessLevel(username).map {
      case Some((dbPassword: String, accessLevel: Int)) =>
        if (password.isBcryptedSafe(dbPassword)
          .getOrElse(false)) {
          logger.info(s"Authorized user $username")
          Some((username, accessLevel))
        } else {
          logger.warn(s"Unsuccessful authorization attempt for user $username!")
          None
        }

      case _ => None
    }
  }
}