package edu.arch.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

public class LocalShopApplication {
    public static void main(String[] args) {
        // Broker startuje bez topiców. KafkaAdmin utworzy je z beanów NewTopic.
        var broker = new EmbeddedKafkaKraftBroker(1, 1);
        try {
            broker.afterPropertiesSet();
            var application = new SpringApplication(ShopApplication.class);
            application.setAdditionalProfiles("local");
            application.run(args);
            // Broker zamykamy po listenerach i producencie.
            SpringApplication.getShutdownHandlers().add(broker::destroy);
        } catch (RuntimeException | Error ex) {
            broker.destroy();
            throw ex;
        }
    }
}
