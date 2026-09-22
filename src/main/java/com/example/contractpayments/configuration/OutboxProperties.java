package com.example.contractpayments.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payments.outbox")
public record OutboxProperties(boolean enabled, String topic, int batchSize, Duration pollDelay,
        Duration lease, Duration baseRetryDelay, Duration maximumRetryDelay, int workerCount) { }
