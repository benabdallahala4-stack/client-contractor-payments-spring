package com.example.contractpayments.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.contractpayments.outbox.application.PublishOutboxBatch;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class KafkaPublicationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PublishOutboxBatch publisher;
    @Autowired Environment environment;

    @BeforeEach void clean() { jdbc.execute("TRUNCATE outbox_events, payments, jobs, contracts, profiles CASCADE"); }

    @Test void publishesVersionedJobPaidEnvelopeAndMarksOutboxPublished() {
        UUID eventId = UUID.randomUUID(), jobId = UUID.randomUUID();
        String payload = "{\"eventId\":\"%s\",\"eventType\":\"JobPaid\",\"eventVersion\":1,\"jobId\":\"%s\"}"
            .formatted(eventId, jobId);
        Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO outbox_events
              (id, aggregate_type, aggregate_id, event_type, event_version, payload, status,
               attempt_count, available_at, created_at)
            VALUES (?, 'Job', ?, 'JobPaid', 1, ?::jsonb, 'PENDING', 0, ?, ?)
            """, eventId, jobId, payload, Timestamp.from(now), Timestamp.from(now));

        String bootstrap = environment.getProperty("spring.kafka.bootstrap-servers");
        var config = Map.<String, Object>of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG, "publication-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(java.util.List.of("job-paid.v1"));
            consumer.poll(Duration.ofMillis(500));
            assertThat(publisher.run(now.plusSeconds(1), 10)).isEqualTo(1);
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).anySatisfy(record -> {
                assertThat(record.key()).isEqualTo(jobId.toString());
                assertThat(record.value()).contains(eventId.toString(), "\"eventVersion\"", "1");
            });
        }
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, eventId))
            .isEqualTo("PUBLISHED");
    }
}
