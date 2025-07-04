package org.mifos.loanrisk.document.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.document.v1.DocumentDataV1;
import org.apache.fineract.avro.generic.v1.EnumOptionDataV1; // Will be replaced
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.apache.fineract.avro.loan.v1.LoanStatusEnumDataV1; // Assuming this specific type exists
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.mifos.loanrisk.domain.Aggregator; // Added import
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.config.AbstractIntegrationTest;
import org.mifos.loanrisk.repository.AggregatorRepository;
import org.mifos.loanrisk.service.AggregatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DocumentEventHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private DocumentCreatedHandler documentCreatedHandler;

    @Autowired
    private DocumentDeletedHandler documentDeletedHandler;

    @Autowired
    private AggregatorService aggregatorService; // To set up preconditions

    @Autowired
    private AggregatorRepository aggregatorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        aggregatorRepository.deleteAll().block();
    }

    // Helper from AggregatorServiceTest, simplified for focus
    private LoanAccountDataV1 createTestLoanAccountDataV1(Long loanId, String clientExternalId, Integer loanStatusId) {
        return LoanAccountDataV1.newBuilder()
                .setId(loanId)
                .setClientExternalId(clientExternalId)
                .setStatus(LoanStatusEnumDataV1.newBuilder().setId(loanStatusId).setCode("STATUS").setValue("Status").build()) // Using builder
                // Add other minimal fields required by Aggregator constructor if not already covered by defaults
                .setAccountNo("LN-" + loanId).setExternalId("EXT-" + loanId).setClientId(loanId * 10)
                .setClientAccountNo("CL-ACC-" + loanId).setLoanProductId(1L).setLoanProductName("Test Loan Product")
                // .setCurrencyCode("USD") // Temporarily removed
                .setPrincipal(BigDecimal.valueOf(1000.0))
                // .setSubmittedOnTimestamp(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setApprovedOnDate(null) // Temporarily removed
                // .setExpectedDisbursementDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setDisbursedOnDate(null) // Temporarily removed
                // .setExpectedMaturityDate(LocalDate.now().plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .setApprovedPrincipal(BigDecimal.valueOf(1000.00))
                // .setArrearsTolerance(BigDecimal.ZERO) // Temporarily removed
                .setNumberOfRepayments(12)
                .setRepaymentEvery(1)
                .setRepaymentFrequencyType(new EnumOptionDataV1(2, "MONTHS", "Months")) // Using int
                .setInterestRatePerPeriod(BigDecimal.valueOf(1.5))
                .setInterestRateFrequencyType(new EnumOptionDataV1(2, "MONTHS", "Months")) // Using int
                .setAnnualInterestRate(BigDecimal.valueOf(18.00))
                .setInterestType(new EnumOptionDataV1(0, "DECLINING_BALANCE", "Declining Balance")) // Using int
                .setAmortizationType(new EnumOptionDataV1(0, "EQUAL_INSTALLMENTS", "Equal Installments")) // Using int
                .setInterestCalculationPeriodType(new EnumOptionDataV1(0, "DAILY", "Daily")) // Using int
                .setExpectedFirstRepaymentOnDate(LocalDate.now().plusMonths(1).toString()) // Changed to String
                .setLoanType(new EnumOptionDataV1(0, "INDIVIDUAL", "Individual")) // Using int
                // .setFeeCharges(Collections.emptyList()) // Temporarily removed
                // .setCollateral(Collections.emptyList()) // Temporarily removed
                // .setGuaranteeDetails(Collections.emptyList()) // Temporarily removed
                // .setPaymentTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setFrequencyOptions(Collections.emptyList()) // Temporarily removed
                // .setInterestRateTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setAmortizationTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setInterestCalculationPeriodTypeOptions(Collections.emptyList()) // Temporarily removed
                .setTransactionProcessingStrategyCode("mifos-standard-strategy")
                .setDaysInYearType(new EnumOptionDataV1(1, "ACTUAL", "Actual")) // Using int
                .setDaysInMonthType(new EnumOptionDataV1(1, "ACTUAL", "Actual")) // Using int
                // .setLoanSchedules(Collections.emptyList()) // Temporarily removed
                // .setLastModifiedDate(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .setLastModifiedBy("test_user")
                .setSubmittedBy("test_user")
                .setLoanDecisionState(new EnumOptionDataV1(0, "PENDING", "Pending")) // Using int
                .setTotalPrincipalDisbursed(BigDecimal.ZERO)
                .setTotalPrincipalExpected(BigDecimal.valueOf(1000.00))
                .build();
    }

    private DocumentDataV1 createTestDocumentDataV1(Long docId, Long loanId, String parentEntityType, String docName, String fileName) {
        return DocumentDataV1.newBuilder()
                .setId(docId)
                .setParentEntityId(loanId)
                .setParentEntityType(parentEntityType)
                .setName(docName)
                .setFileName(fileName)
                .setSize(1024L)
                .setType("application/pdf")
                .setDescription("Test document description")
                // .setLocation("test/location/" + fileName) // Removed: Method does not exist on builder
                .setStorageType(0)
                // .setCreatedByUsername("test_user") // Temporarily removed
                // .setCreatedOn(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setLastModifiedBy("test_user") // Temporarily removed
                // .setLastModifiedOn(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .build();
    }

    private JsonNode toJsonNode(Object object) throws IOException {
        return objectMapper.readTree(objectMapper.writeValueAsString(object));
    }

    @Test
    void documentCreatedHandler_canBeInvoked() throws IOException {
        // This test mainly verifies that the handler can be called without runtime errors.
        // As the handler is currently a stub, this test is minimal.
        DocumentDataV1 docData = createTestDocumentDataV1(1L, 100L, "loan", "Bank Statement", "bs.pdf");
        JsonNode payload = toJsonNode(docData);

        // No exception expected
        documentCreatedHandler.handle(payload);
    }

    @Test
    @Disabled("Test to be enabled when DocumentCreatedHandler implements logic to call AggregatorService")
    void documentCreatedHandler_shouldUpdateAggregator_whenImplemented() throws IOException, InterruptedException {
        Long loanId = 20L;
        // Pre-condition: Aggregator exists
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-20", 100);
        aggregatorService.onLoanCreated(loanData).block();

        DocumentDataV1 docData = createTestDocumentDataV1(2L, loanId, "loan", "Bank Statement", "bs.pdf");
        JsonNode payload = toJsonNode(docData);

        documentCreatedHandler.handle(payload);
        Thread.sleep(500); // Allow for async processing if handler becomes async

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void documentDeletedHandler_canBeInvoked() throws IOException {
        // Similar to created handler, this is a minimal test for the current stub.
        DocumentDataV1 docData = createTestDocumentDataV1(3L, 101L, "loan", "Bank Statement", "bs.pdf");
        JsonNode payload = toJsonNode(docData);

        // No exception expected
        documentDeletedHandler.handle(payload);
    }

    @Test
    @Disabled("Test to be enabled when DocumentDeletedHandler implements logic to call AggregatorService")
    void documentDeletedHandler_shouldUpdateAggregator_whenImplemented() throws IOException, InterruptedException {
        Long loanId = 21L;
        // Pre-condition: Aggregator exists and document was "uploaded"
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-21", 100);
        aggregatorService.onLoanCreated(loanData).block();
        DocumentDataV1 docData = createTestDocumentDataV1(4L, loanId, "loan", "Bank Statement", "bs.pdf");
        // Simulate document creation effect directly via service for setup
        aggregatorService.onDocumentCreated(docData).block();


        // Verify it's initially true
        Aggregator aggregatorBeforeDelete = aggregatorRepository.findByLoanId(loanId).block();
        assertThat(aggregatorBeforeDelete.getBankStmtUploaded()).isTrue();

        // Now, handler for deletion
        JsonNode payload = toJsonNode(docData);
        documentDeletedHandler.handle(payload);
        Thread.sleep(500); // Allow for async processing

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isFalse();
                })
                .verifyComplete();
    }
}
