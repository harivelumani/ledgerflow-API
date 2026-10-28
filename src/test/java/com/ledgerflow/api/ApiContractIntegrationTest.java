package com.ledgerflow.api;

import com.ledgerflow.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiContractIntegrationTest extends IntegrationTestSupport {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void thereAreNoUpdateOrDeleteEndpoints() throws Exception {
        boolean mutating = handlerMapping.getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getMethodsCondition)
                .anyMatch(condition -> condition.getMethods().contains(RequestMethod.PUT)
                        || condition.getMethods().contains(RequestMethod.PATCH)
                        || condition.getMethods().contains(RequestMethod.DELETE));
        assertThat(mutating).isFalse();

        UUID accountId = createAccount("Immutable");
        var transaction = postTransaction(accountId, "CREDIT", "5.00", "Salary", "SALARY-001");
        UUID transactionId = UUID.fromString(transaction.get("id").asText());

        mockMvc.perform(put("/api/accounts/{accountId}/transactions/{transactionId}", accountId, transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/accounts/{accountId}/transactions/{transactionId}", accountId, transactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/accounts/{accountId}/transactions/{transactionId}", accountId, transactionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(put("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(delete("/api/accounts/{accountId}/transactions", accountId))
                .andExpect(status().isMethodNotAllowed());

        assertThat(balanceOf(accountId)).isEqualByComparingTo("5.00");
    }

    @Test
    void openApiDocumentsTheLedgerEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Transaction Ledger API"))
                .andExpect(jsonPath("$.paths['/api/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountId}/balance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountId}/transactions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/accounts/{accountId}/transactions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/transfers'].post").exists());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void readingBalanceDoesNotMutateIt() throws Exception {
        UUID accountId = createAccount("Read");
        postTransaction(accountId, "CREDIT", "12.00", "Seed", "SEED");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("12.00");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("12.00");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_transactions WHERE account_id = ?", Integer.class, accountId.toString());
        assertThat(count).isEqualTo(1);
    }
}
