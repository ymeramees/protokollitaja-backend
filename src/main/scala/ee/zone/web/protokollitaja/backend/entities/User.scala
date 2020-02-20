package ee.zone.web.protokollitaja.backend.entities

import org.mongodb.scala.bson.ObjectId

object User {
  def apply(username: String, password: String, accessLevel: Int): User =
    User(new ObjectId(), username, password, accessLevel)
}

/*
  Access levels:
  0 - not approved
  1 - read-write: competitions, read-only otherwise
  2 - read-write: everything, except users
  3 - admin: read-write everything
 */

case class User(_id: ObjectId, username: String, password: String, accessLevel: Int)
