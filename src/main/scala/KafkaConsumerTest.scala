import java.util
import java.util.Properties

import ee.zone.web.protokollitaja.backend.entities.Competitor
import ee.zone.web.protokollitaja.backend.proto.PSeries
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._

object KafkaConsumerTest /*extends App*/ {

  implicit val formats = DefaultFormats

  def consumeFromKafka(topic: String): Unit = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    props.put("auto.offset.reset", "earliest")
    props.put("group.id", "consumer-group")
    val consumer = new KafkaConsumer[String, String](props)
//    consumer.subscribe(util.Arrays.asList(topic))
//    consumer.poll(java.time.Duration.ofMillis(100)) // Dummy poll
    val topicPartition = util.Arrays.asList(new TopicPartition("test", 0))
    consumer.assign(topicPartition)
//    val topicPartition = consumer.assignment()
    consumer.seekToBeginning(topicPartition)
    while (true) {
      val record = consumer.poll(java.time.Duration.ofMillis(100)).asScala
      for (data <- record.iterator) {
        println(data.value())
        try {
          val json = parse(data.value())
          val competitor = json.extract[Competitor]
          println(s"Competitor: $competitor")
        } catch {
          case _: Throwable => println("String not working!")
        }
      }
    }
  }

  def consumeFromKafkaBinary(topic: String): Unit = {
    val props = new Properties()
    props.put("bootstrap.servers", "localhost:9092")
    props.put("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
    props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
    props.put("auto.offset.reset", "earliest")
    props.put("group.id", "consumer-group")
    val consumer = new KafkaConsumer[String, Array[Byte]](props)
    //    consumer.subscribe(util.Arrays.asList(topic))
    //    consumer.poll(java.time.Duration.ofMillis(100)) // Dummy poll
    val topicPartition = util.Arrays.asList(new TopicPartition("test", 0))
    consumer.assign(topicPartition)
    //    val topicPartition = consumer.assignment()
    consumer.seekToBeginning(topicPartition)
    while (true) {
      val record = consumer.poll(java.time.Duration.ofMillis(100)).asScala
      for (data <- record.iterator) {
        println(data.value())
        try {
          val competitor = PSeries.parseFrom(data.value())
//          val competitor = json.extract[Competitor]
          println(s"Competitor: $competitor")
        } catch {
          case _: Throwable => println("Binary not working!")
        }
      }
    }
  }

  println("Starting...")
//  consumeFromKafka("test")
  consumeFromKafkaBinary("test")
}
