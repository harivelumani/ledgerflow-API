package com.ledgerflow.api;

import com.ledgerflow.repository.LedgerTransactionRepository;
import com.ledgerflow.repository.TransferRepository;
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

class TransferIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void transferMovesMoneyAndLinksBothLedgerEntries() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "100.00", "Seed", "SEED");

        var result = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceAccountId":"%s",
                                  "destinationAccountId":"%s",
                                  "amount":30.00,
                                  "referenceId":"TRANSFER-001",
                                  "description":"Transfer to savings"
                                }
                                """.formatted(source, destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId").value("TRANSFER-001"))
                .andReturn();
        var body = json(result);
        assertThat(body.get("sourceBalance").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(body.get("destinationBalance").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(balanceOf(source)).isEqualByComparingTo("70.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("30.00");

        UUID transferId = UUID.fromString(body.get("id").asText());
        UUID debitId = UUID.fromString(body.get("debitTransactionId").asText());
        UUID creditId = UUID.fromString(body.get("creditTransactionId").asText());
        var debit = ledgerTransactionRepository.findById(debitId).orElseThrow();
        var credit = ledgerTransactionRepository.findById(creditId).orElseThrow();
        assertThat(debit.getTransferId()).isEqualTo(transferId);
        assertThat(credit.getTransferId()).isEqualTo(transferId);
        assertThat(debit.getAccountId()).isEqualTo(source);
        assertThat(credit.getAccountId()).isEqualTo(destination);
        assertThat(debit.getType().name()).isEqualTo("DEBIT");
        assertThat(credit.getType().name()).isEqualTo("CREDIT");
        assertThat(debit.getReferenceId()).isEqualTo("TRANSFER-001");
        assertThat(credit.getReferenceId()).isEqualTo("TRANSFER-001");

        var sourceHistory = json(mockMvc.perform(get("/api/accounts/{id}/transactions", source).param("type", "DEBIT"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(sourceHistory.get("content").findValuesAsText("referenceId")).contains("TRANSFER-001");
    }

    @Test
    void rejectsInsufficientFundsMissingAccountsSameAccountAndInvalidAmount() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "10.00", "Seed", "SEED");
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "11.00", "T-OVER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        assertThat(balanceOf(source)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("0.00");
        assertThat(transferRepository.count()).isZero();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(missing, destination, "1.00", "T-MISS-SRC")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.details[0]").value("Source account does not exist: " + missing));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, missing, "1.00", "T-MISS-DST")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details[0]").value("Destination account does not exist: " + missing));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, source, "1.00", "T-SAME")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "0.00", "T-ZERO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(source, destination, "-2.00", "T-NEG")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationAccountId":"%s","amount":1.00,"referenceId":"T-NO-SRC","description":"x"}
                                """.formatted(destination)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("sourceAccountId is required"));

        assertThat(balanceOf(source)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("0.00");
        assertThat(transferRepository.count()).isZero();
        assertThat(ledgerTransactionRepository.countByAccountId(source)).isEqualTo(1);
    }

    @Test
    void duplicateTransferDoesNotApplyTwice() throws Exception {
        UUID source = createAccount("Source");
        UUID destination = createAccount("Destination");
        postTransaction(source, "CREDIT", "80.00", "Seed", "SEED");
        String body = transferJson(source, destination, "15.00", "TRANSFER-001");

        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REFERENCE"))
                .andExpect(jsonPath("$.message").value("Transfer reference already exists"));

        assertThat(balanceOf(source)).isEqualByComparingTo("65.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("15.00");
        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(ledgerTransactionRepository.countByAccountId(source)).isEqualTo(2);
        assertThat(ledgerTransactionRepository.countByAccountId(destination)).isEqualTo(1);
    }

    private String transferJson(UUID source, UUID destination, String amount, String reference) {
        return """
                {
                  "sourceAccountId":"%s",
                  "destinationAccountId":"%s",
                  "amount":%s,
                  "referenceId":"%s",
                  "description":"Move"
                }
                """.formatted(source, destination, amount, reference);
    }
}
