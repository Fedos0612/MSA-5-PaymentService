package ru.orchestrpay.payment.config;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ZeebeConfiguration {

    @Bean(destroyMethod = "close")
    public ZeebeClient zeebeClient(
            @Value("${ZEEBE_ADDRESS:localhost:26500}") String zeebeAddress
    ) {
        return ZeebeClient.newClientBuilder()
                .gatewayAddress(zeebeAddress)
                .usePlaintext()
                .defaultRequestTimeout(Duration.ofSeconds(30))
                .build();
    }
}