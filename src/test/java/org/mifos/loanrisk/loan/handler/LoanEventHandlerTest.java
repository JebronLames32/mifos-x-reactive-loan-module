package org.mifos.loanrisk.loan.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.generic.v1.EnumOptionDataV1;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.apache.fineract.avro.loan.v1.LoanStatusEnumDataV1; // Assuming this specific type exists
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.config.AbstractIntegrationTest;
import org.mifos.loanrisk.domain.Aggregator;
import org.mifos.loanrisk.domain.LoanSnapshot;
import org.mifos.loanrisk.repository.AggregatorRepository;
import org.mifos.loanrisk.repository.LoanSnapshotRepository;
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
class LoanEventHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private LoanCreatedHandler loanCreatedHandler;

    @Autowired
    private LoanUpdatedHandler loanUpdatedHandler;

    @Autowired
    private AggregatorRepository aggregatorRepository;

    @Autowired
    private LoanSnapshotRepository loanSnapshotRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        aggregatorRepository.deleteAll().block();
        loanSnapshotRepository.deleteAll().block();
    }

    private LoanAccountDataV1 createTestLoanAccountDataV1(Long loanId, String clientExternalId, Integer loanStatusId) {
        // Using a simplified builder for brevity in tests, assuming full object isn't always needed for handler logic
        // Or reuse the comprehensive one from AggregatorServiceTest if full detail is critical
        return LoanAccountDataV1.newBuilder()
                .setId(loanId)
                .setAccountNo("LN-" + loanId)
                .setExternalId("EXT-LN-" + loanId)
                .setClientId(loanId * 10)
                .setClientAccountNo("CL-ACC-" + loanId)
                .setClientExternalId(clientExternalId)
                .setLoanProductId(1L)
                .setLoanProductName("Test Loan Product")
                .setStatus(LoanStatusEnumDataV1.newBuilder().setId(loanStatusId).setCode("STATUS_CODE").setValue("Status Description").build()) // Using builder
                // .setCurrencyCode("USD") // Temporarily removed
                .setPrincipal(BigDecimal.valueOf(10000.00))
                // Add other essential fields that are read by the handlers or Aggregator/LoanSnapshot constructors
                 // .setSubmittedOnTimestamp(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setApprovedOnDate(null) // Temporarily removed
                // .setExpectedDisbursementDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setDisbursedOnDate(null) // Temporarily removed
                // .setExpectedMaturityDate(LocalDate.now().plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .setApprovedPrincipal(BigDecimal.valueOf(10000.00))
                // .setArrearsTolerance(BigDecimal.ZERO) // Temporarily removed
                .setNumberOfRepayments(12)
                .setRepaymentEvery(1)
                .setRepaymentFrequencyType(new EnumOptionDataV1(2, "MONTHS", "Months"))  // Using int
                .setInterestRatePerPeriod(BigDecimal.valueOf(1.5))
                .setInterestRateFrequencyType(new EnumOptionDataV1(2, "MONTHS", "Months")) // Using int
                .setAnnualInterestRate(BigDecimal.valueOf(18.00))
                .setInterestType(new EnumOptionDataV1(0, "DECLINING_BALANCE", "Declining Balance")) // Using int
                .setAmortizationType(new EnumOptionDataV1(0, "EQUAL_INSTALLMENTS", "Equal Installments")) // Using int
                .setInterestCalculationPeriodType(new EnumOptionDataV1(0, "DAILY", "Daily")) // Using int
                .setExpectedFirstRepaymentOnDate(LocalDate.now().plusMonths(1).toString()) // Changed to String
                .setLoanType(new EnumOptionDataV1(0, "INDIVIDUAL", "Individual")) // Using int
                 // Fill in other fields as used by Aggregator(LoanAccountDataV1) constructor
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
                // .setLastModifiedBy("test_user") // Temporarily removed
                .setSubmittedBy("test_user")
                .setLoanDecisionState(new EnumOptionDataV1(0, "PENDING", "Pending")) // Using int
                .setTotalPrincipalDisbursed(BigDecimal.ZERO)
                .setTotalPrincipalExpected(BigDecimal.valueOf(10000.00))
                .build();
    }

    private JsonNode toJsonNode(Object object) throws IOException {
        return objectMapper.readTree(objectMapper.writeValueAsString(object));
    }

    @Test
    void loanCreatedHandler_shouldCreateAggregatorAndLoanSnapshot() throws IOException, InterruptedException {
        Long loanId = 10L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-10", 100); // PENDING_APPROVAL
        JsonNode payload = toJsonNode(loanData);

        loanCreatedHandler.handle(payload);

        // Handlers are async (return void and use .subscribe()), so we need to wait a bit for persistence.
        // In a real scenario, a CountDownLatch or Awaitility would be better than Thread.sleep.
        // For StepVerifier, if the handle method returned a Mono, we could chain it.
        // Since it's void, we block/wait briefly.
        Thread.sleep(1000); // Adjust as necessary, or use a more robust waiting mechanism

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator).isNotNull();
                    assertThat(aggregator.getLoanId()).isEqualTo(loanId);
                    assertThat(aggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
                })
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.findByLoanId(loanId))
                .assertNext(snapshot -> {
                    assertThat(snapshot).isNotNull();
                    assertThat(snapshot.getLoanId()).isEqualTo(loanId);
                    assertThat(snapshot.getPayload()).isNotNull();
                    // Potentially deserialize payload and compare with original loanData
                })
                .verifyComplete();
    }

    @Test
    void loanCreatedHandler_shouldNotRecreateSnapshot_ifOneExists() throws IOException, InterruptedException {
        Long loanId = 11L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-11", 100);
        JsonNode payload = toJsonNode(loanData);

        // First call - creates snapshot
        loanCreatedHandler.handle(payload);
        Thread.sleep(500); // Wait for async operations

        LoanSnapshot firstSnapshot = loanSnapshotRepository.findByLoanId(loanId).block();
        assertThat(firstSnapshot).isNotNull();

        // Second call - should not create a new snapshot, existing one remains
        loanCreatedHandler.handle(payload);
        Thread.sleep(500); // Wait for async operations. TODO: Refactor handler to return Mono<Void> for better testing.

        // Verify only one snapshot exists and its properties are unchanged.
        final LoanSnapshot firstSnapshotFromDb = firstSnapshot; // effectively final for lambda
        StepVerifier.create(loanSnapshotRepository.findByLoanId(loanId))
            .assertNext(snapshotAfterSecondCall -> {
                assertThat(snapshotAfterSecondCall).isNotNull();
                assertThat(snapshotAfterSecondCall.getId()).isEqualTo(firstSnapshotFromDb.getId());
                assertThat(snapshotAfterSecondCall.getSnapshotAt()).isEqualTo(firstSnapshotFromDb.getSnapshotAt());
                assertThat(snapshotAfterSecondCall.getPayload()).isEqualTo(firstSnapshotFromDb.getPayload());
            })
            .verifyComplete();

        // Also assert count if a count method is available and preferred for clarity
        // For example, if loanSnapshotRepository had countByLoanId:
        // StepVerifier.create(loanSnapshotRepository.countByLoanId(loanId))
        // .expectNext(1L)
        // .verifyComplete();
    }


    @Test
    void loanUpdatedHandler_shouldUpdateAggregatorAndLoanSnapshot() throws IOException, InterruptedException {
        Long loanId = 12L;
        // Initial creation
        LoanAccountDataV1 initialLoanData = createTestLoanAccountDataV1(loanId, "tenant-12", 100); // PENDING_APPROVAL
        JsonNode initialPayload = toJsonNode(initialLoanData);
        loanCreatedHandler.handle(initialPayload);
        Thread.sleep(1000); // Wait

        Aggregator initialAggregator = aggregatorRepository.findByLoanId(loanId).block();
        LoanSnapshot initialSnapshot = loanSnapshotRepository.findByLoanId(loanId).block();
        assertThat(initialAggregator).isNotNull();
        assertThat(initialSnapshot).isNotNull();
        assertThat(initialAggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);

        // Update
        LoanAccountDataV1 updatedLoanData = createTestLoanAccountDataV1(loanId, "tenant-12-updated", 300); // ACTIVE
        // Ensure some fields that snapshot stores are different
        updatedLoanData.setPrincipal(BigDecimal.valueOf(12000.00));
        JsonNode updatedPayload = toJsonNode(updatedLoanData);

        loanUpdatedHandler.handle(updatedPayload);
        Thread.sleep(1000); // Wait

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getLoanId()).isEqualTo(loanId);
                    assertThat(aggregator.getTenantId()).isEqualTo("tenant-12-updated");
                    assertThat(aggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.ACTIVE);
                    assertThat(aggregator.getLastUpdated()).isAfter(initialAggregator.getLastUpdated());
                })
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.findByLoanId(loanId))
                .assertNext(snapshot -> {
                    assertThat(snapshot.getLoanId()).isEqualTo(loanId);
                    assertThat(snapshot.getSnapshotAt()).isAfter(initialSnapshot.getSnapshotAt());
                    assertThat(snapshot.getPayload()).contains("\"principal\":12000.00"); // Verify updated payload
                })
                .verifyComplete();
    }

    @Test
    void loanUpdatedHandler_shouldFail_ifSnapshotDoesNotExist() throws IOException {
        Long loanId = 13L;
        LoanAccountDataV1 updatedLoanData = createTestLoanAccountDataV1(loanId, "tenant-13", 300);
        JsonNode updatedPayload = toJsonNode(updatedLoanData);

        // No prior call to loanCreatedHandler, so no snapshot exists.
        // The handler's internal .subscribe() will catch the error.
        // To test this properly, we'd need to either:
        // 1. Refactor handler to return Mono<Void> to use StepVerifier.expectError.
        // 2. Use a test mechanism to capture logs or verify mock interactions if the error is only logged.
        // Current implementation of LoanUpdatedHandler logs error and completes.
        // For now, we assert that no snapshot or aggregator is created/updated.

        loanUpdatedHandler.handle(updatedPayload);
        // We expect an error to be logged by the handler.
        // Verifying that no records were created/updated as a side effect of failure.
         try {
            Thread.sleep(500); // Allow time for async processing if any part succeeded before error
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }


        StepVerifier.create(aggregatorRepository.existsByLoanId(loanId))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(loanSnapshotRepository.existsByLoanId(loanId))
                .expectNext(false)
                .verifyComplete();
        // This test primarily ensures the flow doesn't create partial data on error.
        // A more direct test of the error condition would require refactoring the handler or using log capture.
    }
}
