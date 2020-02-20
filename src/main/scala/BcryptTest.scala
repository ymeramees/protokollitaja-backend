import com.github.t3hnar.bcrypt._

object BcryptTest /*extends App*/ {
  val salt = generateSalt
  println(s"Tulemus: ${"YkseRitiT0rePar0ol".isBcryptedSafe("$2a$12$nwWQGeqNGP7vigoXKvvC9OBHN0XCo/3H7QiHmPJiAGER69YapC2UG")}") // Hash must start with $2a

//  val exception = new NullPointerException("See on test")
//  println(s"Ex: $exception, ${exception.printStackTrace()}")
}
