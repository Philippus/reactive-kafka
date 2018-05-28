/*
 * Copyright (C) 2014 - 2016 Softwaremill <http://softwaremill.com>
 * Copyright (C) 2016 - 2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.kafka.benchmarks

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.TransactionalMessage
import akka.kafka.ProducerMessage.{Message, Result}
import akka.kafka.benchmarks.app.RunTestCommand
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerMessage, ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Source}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer, StringSerializer}

import scala.concurrent.duration.FiniteDuration

case class ReactiveKafkaTransactionTestFixture[SOut, FIn, FOut](sourceTopic: String, sinkTopic: String, msgCount: Int,
    source: Source[SOut, Control],
    flow: Flow[FIn, FOut, NotUsed])

object ReactiveKafkaTransactionFixtures extends PerfFixtureHelpers {
  type Key = Array[Byte]
  type Val = String
  type KTransactionMessage = TransactionalMessage[Key, Val]
  type KProducerMessage = Message[Key, Val, ConsumerMessage.PartitionOffset]
  type KResult = Result[Key, Val, ConsumerMessage.PartitionOffset]

  private def createConsumerSettings(kafkaHost: String)(implicit actorSystem: ActorSystem) =
    ConsumerSettings(actorSystem, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaHost)
      .withGroupId(randomId())
      .withClientId(randomId())
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private def createProducerSettings(kafkaHost: String)(implicit actorSystem: ActorSystem): ProducerSettings[Array[Byte], String] =
    ProducerSettings(actorSystem, new ByteArraySerializer, new StringSerializer)
      .withBootstrapServers(kafkaHost)

  def transactionalSourceAndSink(c: RunTestCommand, commitInterval: FiniteDuration)(implicit actorSystem: ActorSystem) =
    FixtureGen[ReactiveKafkaTransactionTestFixture[KTransactionMessage, KProducerMessage, KResult]](c, msgCount => {
      val sourceTopic = randomId()
      fillTopic(c.kafkaHost, sourceTopic, msgCount)
      val sinkTopic = randomId()

      val consumerSettings = createConsumerSettings(c.kafkaHost)
      val source: Source[KTransactionMessage, Control] = Consumer.transactionalSource(consumerSettings, Subscriptions.topics(sourceTopic))

      val producerSettings = createProducerSettings(c.kafkaHost).withEosCommitInterval(commitInterval)
      val flow: Flow[KProducerMessage, KResult, NotUsed] = Producer.transactionalFlow(producerSettings, randomId())

      ReactiveKafkaTransactionTestFixture[KTransactionMessage, KProducerMessage, KResult](sourceTopic, sinkTopic, msgCount, source, flow)
    })

  def noopFixtureGen(c: RunTestCommand) =
    FixtureGen[ReactiveKafkaTransactionTestFixture[KTransactionMessage, KProducerMessage, KResult]](c, msgCount => {
      ReactiveKafkaTransactionTestFixture("sourceTopic", "sinkTopic", msgCount, source = null, flow = null)
    })
}
