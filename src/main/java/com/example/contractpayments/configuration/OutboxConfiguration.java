package com.example.contractpayments.configuration;

import com.example.contractpayments.outbox.adapter.out.kafka.KafkaEventPublisher;
import com.example.contractpayments.outbox.application.EventPublisher;
import com.example.contractpayments.outbox.application.OutboxStore;
import com.example.contractpayments.outbox.application.OutboxObserver;
import com.example.contractpayments.outbox.application.PublishOutboxBatch;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxConfiguration {
    @Bean KafkaAdmin kafkaAdmin(@Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        return new KafkaAdmin(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap));
    }

    @Bean ProducerFactory<String, String> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.RETRIES_CONFIG, 10);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean NewTopic jobPaidTopic(OutboxProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(3).replicas(1).build();
    }

    @Bean EventPublisher eventPublisher(KafkaTemplate<String, String> kafka, OutboxProperties properties) {
        return new KafkaEventPublisher(kafka, properties);
    }

    @Bean PublishOutboxBatch publishOutboxBatch(OutboxStore store, EventPublisher publisher,
            OutboxProperties properties, OutboxObserver observer) {
        return new PublishOutboxBatch(store, publisher, properties.baseRetryDelay(),
            properties.maximumRetryDelay(), observer);
    }

    @Bean("outboxExecutor") ThreadPoolTaskExecutor outboxExecutor(OutboxProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerCount());
        executor.setMaxPoolSize(properties.workerCount());
        executor.setQueueCapacity(properties.workerCount());
        executor.setThreadNamePrefix("outbox-");
        return executor;
    }
}
