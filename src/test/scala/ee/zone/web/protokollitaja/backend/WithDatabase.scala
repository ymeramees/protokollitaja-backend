package ee.zone.web.protokollitaja.backend

import com.typesafe.config.ConfigFactory
import ee.zone.web.protokollitaja.backend.persistence.Persistence

import scala.concurrent.ExecutionContext

trait WithDatabase {

  private val config = ConfigFactory.load()

  def withDatabase(testCode: Persistence => Any)(implicit ec: ExecutionContext) {
    val persistence = new Persistence(config)
    persistence.cleanUpDatabase()
    try {
      testCode(persistence)
    } finally {
      persistence.cleanUpDatabase()
    }
  }
}
