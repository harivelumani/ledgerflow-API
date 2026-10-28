package com.ledgerflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transaction Ledger API")
                .version("1.0.0")
                .description("""
                        Double-entry style account ledger. Account balances change only inside atomic
                        credit, debit, and transfer operations. Timestamps are UTC. Monetary amounts
                        use scale 2. Transaction and transfer reference IDs are idempotency keys:
                        replaying a reference returns HTTP 409 and does not apply the movement again.
                        """));
    }
}
