package edu.arch.shop.messaging

import edu.arch.shop.support.IntegrationSpec
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.TimeUnit

class KafkaBasicsSpec extends IntegrationSpec {
    @Autowired EmbeddedKafkaBroker broker
    @Autowired KafkaTemplate<String, String> kafkaTemplate

    PollingConditions eventually = new PollingConditions(timeout: 15, delay: 0.1)

    def "wiadomości z tym samym kluczem trafiają do jednej partycji"() {
        given:
        def topic = newTopic(3)
        def consumer = newConsumer("keys-${UUID.randomUUID()}")
        ["first", "second", "third"].each { value ->
            kafkaTemplate.send(topic, "order-42", value).get(10, TimeUnit.SECONDS)
        }

        when:
        consumer.subscribe([topic])
        def records = receive(consumer, 3)

        then:
        records*.key().unique() == ["order-42"]
        records*.partition().unique().size() == 1
        records*.offset() == [0L, 1L, 2L]
        records*.value() == ["first", "second", "third"]

        cleanup:
        consumer?.close()
    }

    def "konsument z tą samą grupą wznawia odczyt od zatwierdzonego offsetu"() {
        given:
        def topic = newTopic(1)
        def group = "resume-${UUID.randomUUID()}"
        def first = newConsumer(group, "earliest")
        kafkaTemplate.send(topic, "order-42", "first").get(10, TimeUnit.SECONDS)
        first.subscribe([topic])
        assert receive(first, 1)*.value() == ["first"]
        first.commitSync()
        first.close()
        kafkaTemplate.send(topic, "order-42", "second").get(10, TimeUnit.SECONDS)

        when:
        def resumed = newConsumer(group, "earliest")
        resumed.subscribe([topic])
        def records = receive(resumed, 1)

        then:
        records*.value() == ["second"]
        records*.offset() == [1L]

        cleanup:
        first?.close()
        resumed?.close()
    }

    def "nowa grupa z earliest odczytuje wcześniejsze wiadomości, a z latest tylko nowe"() {
        given:
        def topic = newTopic(1)
        kafkaTemplate.send(topic, "order-42", "before").get(10, TimeUnit.SECONDS)
        def earliest = newConsumer("earliest-${UUID.randomUUID()}", "earliest")
        def latest = newConsumer("latest-${UUID.randomUUID()}", "latest")
        earliest.subscribe([topic])

        expect:
        receive(earliest, 1)*.value() == ["before"]

        when:
        latest.subscribe([topic])
        eventually.eventually {
            assert latest.poll(Duration.ofMillis(100)).empty
            assert latest.assignment().size() == 1
            assert latest.position(latest.assignment().first()) == 1L
        }
        kafkaTemplate.send(topic, "order-42", "after").get(10, TimeUnit.SECONDS)
        def records = receive(latest, 1)

        then:
        records*.value() == ["after"]
        records*.offset() == [1L]

        cleanup:
        earliest?.close()
        latest?.close()
    }

    private String newTopic(int partitions) {
        def name = "kafka-basics-${UUID.randomUUID()}".toString()
        broker.addTopics(new NewTopic(name, partitions, (short) 1))
        name
    }

    private Consumer<String, String> newConsumer(String group, String offsetReset = "earliest") {
        def properties = KafkaTestUtils.consumerProps(broker, group, false)
        properties["auto.offset.reset"] = offsetReset
        new DefaultKafkaConsumerFactory<String, String>(properties,
                new StringDeserializer(), new StringDeserializer()).createConsumer()
    }

    private List<ConsumerRecord<String, String>> receive(Consumer<String, String> consumer, int count) {
        List<ConsumerRecord<String, String>> records = []
        eventually.eventually {
            consumer.poll(Duration.ofMillis(100)).each { records << it }
            assert records.size() == count
        }
        records
    }
}
