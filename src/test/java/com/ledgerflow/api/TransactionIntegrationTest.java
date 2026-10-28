package com.ledgerflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerflow.repository.LedgerTransactionRepository;
import com.ledgerflow.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Test
    void creditsAndDebitsFollowTheLedgerExample() throws Exception {
        UUID accountId = createAccount("Alice");

        postTransaction(accountId, "CREDIT", "100.00", "Salary", "SALARY-001");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");

        postTransaction(accountId, "CREDIT", "50.00", "Bonus", "BONUS-001");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("150.00");

        postTransaction(accountId, "DEBIT", "30.00", "Rent", "RENT-001");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("120.00");

        JsonNode last = postTransaction(accountId, "DEBIT", "20.00", "Groceries", "GROC-001");
        assertThat(last.get("balance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(last.get("accountId").asText()).isEqualTo(accountId.toString());
        assertThat(last.get("type").asText()).isEqualTo("DEBIT");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(4);
    }

    @Test
    void debitToZeroIsAllowed() throws Exception {
        UUID accountId = createAccount("Zero");
        postTransaction(accountId, "CREDIT", "15.50", "Top up", "TOP-1");
        postTransaction(accountId, "DEBIT", "15.50", "Spend", "SPEND-1");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
    }

    @Test
    void insufficientDebitDoesNotChangeBalanceOrHistory() throws Exception {
        UUID accountId = createAccount("Bob");
        postTransaction(accountId, "CREDIT", "10.00", "Seed", "SEED");

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"DEBIT","amount":10.01,"description":"Too much","referenceId":"OVER-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balanceOf(accountId)).isEqualByComparingTo("10.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAmountsTypesAndReferences() throws Exception {
        UUID accountId = createAccount("Cara");

        expectAmountRejected(accountId, "0.00", "ZERO");
        expectAmountRejected(accountId, "-5.00", "NEG");
        expectAmountRejected(accountId, "1.001", "FRAC");

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":5.00,"description":"Missing ref"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value("referenceId is required"));

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":5.00,"description":"Blank ref","referenceId":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("referenceId is required"));

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":5.00,"description":"No type","referenceId":"NO-TYPE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("type is required"));

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TRANSFER","amount":5.00,"description":"Bad type","referenceId":"BAD-TYPE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_TYPE"))
                .andExpect(jsonPath("$.message").value("Invalid transaction type"))
                .andExpect(jsonPath("$.details[0]").value("type must be CREDIT or DEBIT"));

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":5.00,"referenceId":"NO-DESC"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value("description is required"));

        assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
        assertThat(ledgerTransactionRepository.countByAccountId(accountId)).isZero();
    }

    @Test
    void duplicateReferenceDoesNotChangeBalanceAndIsAllowedOnAnotherAccount() throws Exception {
        UUID first = createAccount("First");
        UUID second = createAccount("Second");
        postTransaction(first, "CREDIT", "40.00", "Salary", "SALARY-001");

        mockMvc.perform(post("/api/accounts/{accountId}/transactions", first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":40.00,"description":"Replay","referenceId":"SALARY-001"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REFERENCE"))
                .andExpect(jsonPath("$.message").value("Transaction reference already exists"));

        assertThat(balanceOf(first)).isEqualByComparingTo("40.00");
        assertThat(ledgerTransactionRepository.countByAccountId(first)).isEqualTo(1);

        postTransaction(second, "CREDIT", "5.00", "Other account", "SALARY-001");
        assertThat(balanceOf(second)).isEqualByComparingTo("5.00");
    }

    @Test
    void missingAccountRejectsTransactionAndHistory() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(post("/api/accounts/{accountId}/transactions", missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":5.00,"description":"Nope","referenceId":"NOPE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(get("/api/accounts/{accountId}/transactions", missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    @Test
    void historyIsFilteredPagedAndSortedLatestFirst() throws Exception {
        UUID accountId = createAccount("History");
        UUID other = createAccount("Other");
        for (int i = 0; i < 7; i++) {
            postTransaction(accountId, "CREDIT", "1.00", "Item " + i, "REF-" + i);
            clock.advance(Duration.ofSeconds(1));
        }
        postTransaction(accountId, "DEBIT", "1.00", "Spend old", "DEB-1");
        clock.advance(Duration.ofSeconds(1));
        postTransaction(accountId, "DEBIT", "1.00", "Spend new", "DEB-2");
        postTransaction(other, "CREDIT", "9.00", "Elsewhere", "REF-0");

        var firstPage = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(firstPage.get("page").asInt()).isZero();
        assertThat(firstPage.get("size").asInt()).isEqualTo(5);
        assertThat(firstPage.get("totalRecords").asInt()).isEqualTo(9);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(texts(firstPage, "referenceId")).containsExactly("DEB-2", "DEB-1", "REF-6", "REF-5", "REF-4");
        assertThat(firstPage.get("content").findValuesAsText("accountId"))
                .allMatch(accountId.toString()::equals);

        var secondPage = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId)
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(secondPage.get("page").asInt()).isEqualTo(1);
        assertThat(texts(secondPage, "referenceId")).containsExactly("REF-3", "REF-2", "REF-1", "REF-0");
        assertThat(secondPage.get("totalRecords").asInt()).isEqualTo(9);
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(2);

        var credits = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId)
                        .param("type", "CREDIT")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(credits.get("content").findValuesAsText("type")).allMatch("CREDIT"::equals);
        assertThat(texts(credits, "referenceId")).containsExactly("REF-6", "REF-5", "REF-4", "REF-3", "REF-2", "REF-1", "REF-0");
        assertThat(credits.get("totalRecords").asInt()).isEqualTo(7);

        var debits = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId)
                        .param("type", "DEBIT")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(debits.get("content").findValuesAsText("type")).containsExactly("DEBIT");
        assertThat(texts(debits, "referenceId")).containsExactly("DEB-1");
        assertThat(debits.get("totalRecords").asInt()).isEqualTo(2);
        assertThat(debits.get("totalPages").asInt()).isEqualTo(2);

        var empty = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", other)
                        .param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(empty.get("content")).isEmpty();
        assertThat(empty.get("totalRecords").asInt()).isZero();
        assertThat(empty.get("totalPages").asInt()).isZero();
        assertThat(empty.get("page").asInt()).isZero();
        assertThat(empty.get("size").asInt()).isEqualTo(20);
    }

    @Test
    void equalTimestampsUseTransactionIdAsTieBreaker() throws Exception {
        UUID accountId = createAccount("Ties");
        JsonNode first = postTransaction(accountId, "CREDIT", "1.00", "A", "A");
        JsonNode second = postTransaction(accountId, "CREDIT", "1.00", "B", "B");
        assertThat(first.get("createdAt").asText()).isEqualTo(second.get("createdAt").asText());

        var page = json(mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId))
                .andExpect(status().isOk())
                .andReturn());
        List<String> ids = page.get("content").findValuesAsText("id");
        // The id column is CHAR(36), so the tie-break matches lexicographic order, not UUID.compareTo.
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(ids).containsExactlyInAnyOrder(first.get("id").asText(), second.get("id").asText());
    }

    @Test
    void rejectsInvalidPaginationAndTypeFilter() throws Exception {
        UUID accountId = createAccount("Pages");
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("size", "-3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("page", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        mockMvc.perform(get("/api/accounts/{accountId}/transactions", accountId).param("type", "WIRE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_TYPE"));
    }

    private void expectAmountRejected(UUID accountId, String amount, String reference) throws Exception {
        mockMvc.perform(post("/api/accounts/{accountId}/transactions", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"CREDIT","amount":%s,"description":"Bad amount","referenceId":"%s"}
                                """.formatted(amount, reference)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private List<String> texts(JsonNode page, String field) {
        return page.get("content").findValuesAsText(field);
    }
}
