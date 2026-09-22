package com.example.contractpayments.configuration;

import com.example.contractpayments.payment.application.PayJobService;
import com.example.contractpayments.payment.application.PayJobUseCase;
import com.example.contractpayments.payment.application.PaymentTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean PayJobUseCase payJobUseCase(PaymentTransaction transaction) {
        return new PayJobService(transaction);
    }
}
