package com.example.contractpayments.outbox.adapter.out.metrics;

import com.example.contractpayments.outbox.application.OutboxObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics implements OutboxObserver {
    private final Counter published;
    private final Counter failures;

    public OutboxMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.published = Counter.builder("payments.outbox.published").register(registry);
        this.failures = Counter.builder("payments.outbox.failures").register(registry);
        Gauge.builder("payments.outbox.pending", () -> count(jdbc)).register(registry);
        Gauge.builder("payments.outbox.oldest.age.seconds", () -> oldestAge(jdbc)).register(registry);
    }

    @Override public void published() { published.increment(); }
    @Override public void failed() { failures.increment(); }

    private static double count(JdbcTemplate jdbc) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Long.class);
        return value == null ? 0 : value;
    }

    private static double oldestAge(JdbcTemplate jdbc) {
        Double value = jdbc.queryForObject("""
            SELECT COALESCE(EXTRACT(EPOCH FROM (now() - min(created_at))), 0)
              FROM outbox_events WHERE status <> 'PUBLISHED'
            """, Double.class);
        return value == null ? 0 : Math.max(0, value);
    }
}
