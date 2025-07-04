package org.mifos.loanrisk.repository;

import org.apache.fineract.avro.generic.v1.EnumOptionDataV1; // Will be replaced by specific if available
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.apache.fineract.avro.loan.v1.LoanStatusEnumDataV1; // Assuming this specific type exists
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.common.LoanStatus;
import org.mifos.loanrisk.common.ServiceStatus;
import org.mifos.loanrisk.config.AbstractIntegrationTest;
import org.mifos.loanrisk.domain.Aggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AggregatorRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private AggregatorRepository aggregatorRepository;

    @BeforeEach
    void cleanup() {
        aggregatorRepository.deleteAll().block();
    }

    // Minimal LoanAccountDataV1 for Aggregator constructor
    private LoanAccountDataV1 createMinimalLoanData(Long loanId, String clientExternalId, int statusId) {
         return LoanAccountDataV1.newBuilder()
                .setId(loanId)
                .setClientExternalId(clientExternalId)
                .setStatus(LoanStatusEnumDataV1.newBuilder().setId(statusId).setCode("STATUS_CODE").setValue("Status Description").build()) // Using builder
                 // Add other essential fields that are read by the Aggregator(LoanAccountDataV1) constructor
                .setAccountNo("LN-" + loanId).setExternalId("EXT-" + loanId).setClientId(loanId * 10)
                .setClientAccountNo("CL-ACC-" + loanId).setLoanProductId(1L).setLoanProductName("Test Loan Product")
                // .setCurrencyCode("USD") // Temporarily removed
                .setPrincipal(BigDecimal.valueOf(10000.00))
                // .setSubmittedOnTimestamp(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setApprovedOnDate(null) // Temporarily removed
                // .setExpectedDisbursementDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setDisbursedOnDate(null) // Temporarily removed
                // .setExpectedMaturityDate(LocalDate.now().plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .setApprovedPrincipal(BigDecimal.valueOf(10000.00))
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
                .setTotalPrincipalExpected(BigDecimal.valueOf(10000.00))
                .build();
    }

    private Aggregator createTestAggregator(Long loanId, String tenantId) {
        LoanAccountDataV1 loanData = createMinimalLoanData(loanId, tenantId, 100); // 100 for PENDING_APPROVAL
        return new Aggregator(loanData);
    }

    @Test
    void saveAndFindById_shouldWork() {
        Aggregator aggregator = createTestAggregator(1L, "tenant1");

        StepVerifier.create(aggregatorRepository.save(aggregator))
                .assertNext(savedAggregator -> {
                    assertThat(savedAggregator.getId()).isNotNull();
                    assertThat(savedAggregator.getLoanId()).isEqualTo(1L);
                    assertThat(savedAggregator.getTenantId()).isEqualTo("tenant1");

                    StepVerifier.create(aggregatorRepository.findById(savedAggregator.getId()))
                            .assertNext(foundAggregator -> {
                                assertThat(foundAggregator.getLoanId()).isEqualTo(1L);
                                assertThat(foundAggregator.getTenantId()).isEqualTo("tenant1");
                                assertThat(foundAggregator.getLoanStatus()).isEqualTo(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void findByLoanId_shouldReturnCorrectAggregator() {
        Aggregator aggregator1 = createTestAggregator(10L, "tenant10");
        Aggregator aggregator2 = createTestAggregator(11L, "tenant11");
        aggregatorRepository.save(aggregator1).block();
        aggregatorRepository.save(aggregator2).block();

        StepVerifier.create(aggregatorRepository.findByLoanId(10L))
                .assertNext(found -> {
                    assertThat(found.getLoanId()).isEqualTo(10L);
                    assertThat(found.getTenantId()).isEqualTo("tenant10");
                })
                .verifyComplete();
    }

    @Test
    void findOneByLoanId_shouldReturnCorrectAggregator() {
        Aggregator aggregator1 = createTestAggregator(10L, "tenant10");
        Aggregator aggregator2 = createTestAggregator(11L, "tenant11");
        aggregatorRepository.save(aggregator1).block();
        aggregatorRepository.save(aggregator2).block();

        StepVerifier.create(aggregatorRepository.findOneByLoanId(10L))
                .assertNext(found -> {
                    assertThat(found.getLoanId()).isEqualTo(10L);
                    assertThat(found.getTenantId()).isEqualTo("tenant10");
                })
                .verifyComplete();
    }


    @Test
    void findByLoanId_shouldReturnEmpty_whenNotFound() {
        StepVerifier.create(aggregatorRepository.findByLoanId(999L))
                .verifyComplete();
    }

    @Test
    void existsByLoanId_shouldReturnTrue_whenExists() {
        Aggregator aggregator = createTestAggregator(20L, "tenant20");
        aggregatorRepository.save(aggregator).block();

        StepVerifier.create(aggregatorRepository.existsByLoanId(20L))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByLoanId_shouldReturnFalse_whenNotExists() {
        StepVerifier.create(aggregatorRepository.existsByLoanId(998L))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void countByLoanId_shouldReturnCorrectCount() {
        Aggregator aggregator1 = createTestAggregator(30L, "tenant30a");
        // Note: loanId should ideally be unique per aggregator record in typical usage.
        // If testing for multiple records with same loanId (not standard), adjust setup.
        // For now, assuming loanId is effectively unique for active aggregators.
        aggregatorRepository.save(aggregator1).block();

        StepVerifier.create(aggregatorRepository.countByLoanId(30L))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.countByLoanId(31L)) // Non-existent
                .expectNext(0L)
                .verifyComplete();
    }


    @Test
    void save_shouldUpdateExistingAggregator_whenSameId() {
        Aggregator originalAggregator = createTestAggregator(50L, "tenant50-original");
        Aggregator savedOriginal = aggregatorRepository.save(originalAggregator).block();
        assertThat(savedOriginal).isNotNull();
        Long generatedId = savedOriginal.getId();

        // Create a new Aggregator instance for update, or modify the fetched one
        Aggregator aggregatorToUpdate = createTestAggregator(50L, "tenant50-updated");
        aggregatorToUpdate.setId(generatedId); // Critical: Set ID to match existing record for update
        aggregatorToUpdate.setBankStmtUploaded(true);
        aggregatorToUpdate.setAssessmentStatus(ServiceStatus.REQUESTED);
        aggregatorToUpdate.setLastUpdated(LocalDateTime.now().plusHours(1));


        StepVerifier.create(aggregatorRepository.save(aggregatorToUpdate))
                .assertNext(updatedAggregator -> {
                    assertThat(updatedAggregator.getId()).isEqualTo(generatedId);
                    assertThat(updatedAggregator.getLoanId()).isEqualTo(50L);
                    assertThat(updatedAggregator.getTenantId()).isEqualTo("tenant50-updated");
                    assertThat(updatedAggregator.getBankStmtUploaded()).isTrue();
                    assertThat(updatedAggregator.getAssessmentStatus()).isEqualTo(ServiceStatus.REQUESTED);
                    assertThat(updatedAggregator.getLastUpdated()).isAfter(savedOriginal.getLastUpdated());
                })
                .verifyComplete();

        // Verify count is still 1 for this ID
        StepVerifier.create(aggregatorRepository.count())
            .expectNext(1L)
            .verifyComplete();
    }
}
