package com.ledgerflow.api;

import com.ledgerflow.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private com.ledgerflow.repository.AccountRepository accountRepository;

    @Test
    void createsAccountWithZeroBalanceAndServerTimestamp() throws Exception {
        var result = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andReturn();

        var body = json(result);
        UUID id = UUID.fromString(body.get("id").asText());
        assertThat(body.get("balance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(body.get("createdAt").asText()).isEqualTo("2026-10-27T10:00:00Z");

        var stored = accountRepository.findById(id).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("0.00");
        assertThat(stored.getCreatedAt()).isEqualTo(START);
        assertThat(balanceOf(id)).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsMissingOrBlankName() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value("name is required"))
                .andExpect(jsonPath("$.path").value("/api/accounts"));

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void rejectsClientSuppliedBalance() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"balance\":500.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void balanceOfMissingAccountIs404() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/accounts/{accountId}/balance", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/accounts/" + missing + "/balance"));
    }

    @Test
    void invalidAccountIdIs400() throws Exception {
        mockMvc.perform(get("/api/accounts/{accountId}/balance", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));
    }
}
