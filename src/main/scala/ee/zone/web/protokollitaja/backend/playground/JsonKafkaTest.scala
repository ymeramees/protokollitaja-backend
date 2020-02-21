package ee.zone.web.protokollitaja.backend.playground

import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization

object JsonKafkaTest /*extends App*/ {
  implicit val formats = Serialization.formats(NoTypeHints)

//  val series = Series("101,3")
//  val pSeries = PSeries(Id = series._id.toString, seriesSum = series.seriesSum)
////  val competitor = Competitor("Peeter", "Pedaal", "Kuulaskurid", List(Shot(102.3, 4.32, 2.53), Shot(101.4, 1.53, 0.123), Shot(104.2, 1.34, -1.34)), 423.4)
//  val competitor = Competitor("Peeter", "Pedaal", "1945", "Kuulaskurid", List(), List(Series("102,3"), series), "102,3", "4", "", "")
//
////  val json = ("competitor" -> competitor)
//  val json = write(competitor)
//
//  println(pretty(parse(json)))
//
//  def writeToKafka(topic: String, message: String): Unit = {
//    val props = new Properties()
//    props.put("bootstrap.servers", "localhost:9092")
//    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
//    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
//    val producer = new KafkaProducer[String, String](props)
//    val record = new ProducerRecord[String, String](topic, message)
//    producer.send(record)
//    producer.close()
//  }
//
//  def writeToKafkaBinary(topic: String, message: Array[Byte]): Unit = {
//    val props = new Properties()
//    props.put("bootstrap.servers", "localhost:9092")
//    props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
//    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")
//    val producer = new KafkaProducer[String, Array[Byte]](props)
//    val record = new ProducerRecord[String, Array[Byte]](topic, message)
//    producer.send(record)
//    producer.close()
//  }
//
////  writeToKafka("test", json)
//  writeToKafkaBinary("test", pSeries.toByteArray)
}
