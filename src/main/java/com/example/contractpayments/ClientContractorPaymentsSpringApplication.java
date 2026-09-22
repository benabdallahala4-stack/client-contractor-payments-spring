package com.example.contractpayments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClientContractorPaymentsSpringApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClientContractorPaymentsSpringApplication.class, args);
	}

}
