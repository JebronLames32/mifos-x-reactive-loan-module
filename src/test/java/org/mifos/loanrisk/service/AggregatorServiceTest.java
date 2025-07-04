package org.mifos.loanrisk.service;

import org.apache.fineract.avro.document.v1.DocumentDataV1;
import org.apache.fineract.avro.generic.v1.EnumOptionDataV1;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.apache.fineract.avro.loan.v1.LoanStatusEnumDataV1; // Added import
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AggregatorServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AggregatorService aggregatorService;

    @Autowired
    private AggregatorRepository aggregatorRepository;

    @Autowired
    private LoanSnapshotRepository loanSnapshotRepository; // Will be used for loan snapshot tests later

    @BeforeEach
    void cleanup() {
        // It's good practice to clean up the repositories before each test,
        // especially when testing creation logic.
        // Liquibase should handle schema creation, so we just delete data.
        aggregatorRepository.deleteAll().block();
        loanSnapshotRepository.deleteAll().block();
    }

    private LoanAccountDataV1 createTestLoanAccountDataV1(Long loanId, String clientExternalId, Integer loanStatusId) {
        return LoanAccountDataV1.newBuilder()
                .setId(loanId)
                .setAccountNo("LN-001")
                .setExternalId("EXT-LN-001")
                .setClientId(1L)
                .setClientAccountNo("CL-ACC-001")
                .setClientExternalId(clientExternalId) // tenantId
                // .setGroupId(null) // Removed: Method does not exist on builder
                .setLoanProductId(1L)
                .setLoanProductName("Test Loan Product")
                // .setSubmittedOnTimestamp(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setApprovedOnDate(null) // Temporarily removed
                // .setExpectedDisbursementDate(LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setDisbursedOnDate(null) // Temporarily removed
                // .setExpectedMaturityDate(LocalDate.now().plusYears(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setCurrencyCode("USD") // Temporarily removed
                .setPrincipal(BigDecimal.valueOf(10000.00))
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
                .setGraceOnPrincipalPayment(0)
                .setGraceOnInterestPayment(0)
                // .setTotalExpectedRepayment(BigDecimal.valueOf(10980.00)) // Temporarily removed
                .setLoanOfficerId(1L)
                .setStatus(LoanStatusEnumDataV1.newBuilder().setId(loanStatusId).setCode("LOAN_AWAITING_DISBURSAL").setValue("Awaiting Disbursal").build()) // Using builder
                .setLoanType(new EnumOptionDataV1(0, "INDIVIDUAL", "Individual")) // Using int
                .setTimeline(null) // Can be fleshed out if needed
                .setSummary(null) // Can be fleshed out if needed
                // .setFeeCharges(Collections.emptyList()) // Temporarily removed
                // .setCollateral(Collections.emptyList()) // Temporarily removed
                // .setGuaranteeDetails(Collections.emptyList()) // Temporarily removed
                // .setPaymentTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setFrequencyOptions(Collections.emptyList()) // Temporarily removed
                // .setInterestRateTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setAmortizationTypeOptions(Collections.emptyList()) // Temporarily removed
                // .setInterestCalculationPeriodTypeOptions(Collections.emptyList()) // Temporarily removed
                .setFundId(1L)
                .setFundName("Test Fund")
                .setLoanPurposeId(1L)
                .setLoanPurposeName("Test Purpose")
                .setTransactionProcessingStrategyCode("mifos-standard-strategy")
                .setTransactionProcessingStrategyName("Mifos Standard Strategy")
                // .setLoanProductLinkedToFloatingRate(false) // Temporarily removed
                .setCanDefineInstallmentAmount(false)
                .setSyncDisbursementWithMeeting(false)
                .setCreateStandingInstructionAtDisbursement(false)
                .setCanUseForTopup(false)
                // .setEnableInstallmentAmount(false) // Temporarily removed
                // .setFixedPrincipalPercentagePerInstallment(null) // Temporarily removed
                .setDaysInYearType(new EnumOptionDataV1(1, "ACTUAL", "Actual"))
                .setDaysInMonthType(new EnumOptionDataV1(1, "ACTUAL", "Actual"))
                .setInterestRecalculationData(null)
                .setPrincipalThresholdForLastInstallment(null)
                .setEnableDownPayment(false)
                .setDisbursedAmountPercentageForDownPayment(null)
                .setEnableAutoRepaymentsForDownPayment(false)
                .setAccountLinkingOptions(Collections.emptyList())
                .setFixedLength(null)
                .setFixedEmiAmount(null)
                .setMaxOutstandingLoanBalance(null)
                .setGraceOnArrearsAgeing(null)
                .setInterestChargedFromDate(null)
                .setExpectedDisbursedOnDate(null)
                .setDelinquencyBucketId(null)
                .setCreditScore(null)
                .setClientScore(null)
                .setReasonForDecision("Initial creation")
                .setDecisionDate(null)
                .setDecisionBy("System")
                .setNotes(null)
                // .setLoanSchedules(Collections.emptyList()) // Temporarily removed
                .setLinkedAccount(null)
                .setInterestRecalculationCompoundingType(null)
                .setRescheduleStrategyMethod(null)
                .setRecalculateInterestForRest(null)
                .setPreClosureInterestCalculationStrategy(null)
                .setCompoundingType(null)
                .setFrequencyTypeForCompounding(null)
                .setFrequencyDateForCompounding(null)
                .setIsTopup(false)
                // .setLastModifiedDate(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setLastModifiedBy("test_user") // Temporarily removed
                .setSubmittedBy("test_user")
                .setApprovedBy(null)
                .setRejectedBy(null)
                .setWithdrawnBy(null)
                .setClosedBy(null)
                .setExpectedApprovedOnDate(null)
                .setExpectedClosedOnDate(null)
                .setLoanDecisionState(new EnumOptionDataV1(0, "PENDING", "Pending")) // Using int
                .setLoanOfficerName("Test Officer")
                .setInterestApplied(null)
                .setFeeApplied(null)
                .setPenaltyApplied(null)
                .setAmountPaid(null)
                .setAmountOverdue(null)
                .setWriteOffApplied(null)
                .setAmountOutstanding(null)
                .setTotalPrincipalDisbursed(BigDecimal.ZERO)
                .setTotalPrincipalExpected(BigDecimal.valueOf(10000.00))
                .setTotalPrincipalPaid(BigDecimal.ZERO)
                .setTotalPrincipalWrittenOff(BigDecimal.ZERO)
                .setTotalInterestCharged(BigDecimal.ZERO)
                .setTotalInterestPaid(BigDecimal.ZERO)
                .setTotalInterestWaived(BigDecimal.ZERO)
                .setTotalInterestWrittenOff(BigDecimal.ZERO)
                .setTotalFeeChargesCharged(BigDecimal.ZERO)
                .setTotalFeeChargesPaid(BigDecimal.ZERO)
                .setTotalFeeChargesWaived(BigDecimal.ZERO)
                .setTotalFeeChargesWrittenOff(BigDecimal.ZERO)
                .setTotalPenaltyChargesCharged(BigDecimal.ZERO)
                .setTotalPenaltyChargesPaid(BigDecimal.ZERO)
                .setTotalPenaltyChargesWaived(BigDecimal.ZERO)
                .setTotalPenaltyChargesWrittenOff(BigDecimal.ZERO)
                .setTotalWaived(BigDecimal.ZERO)
                .setTotalWrittenOff(BigDecimal.ZERO)
                .setTotalOverpaid(BigDecimal.ZERO)
                .setTotalRecovered(BigDecimal.ZERO)
                .setLoanCounter(0)
                .setLoanProductCounter(0)
                .setMultiDisburseLoan(false)
                .setCanDisburse(true)
                .setInArrears(false)
                .setAllowAttributeOverrides(false)
                .setEnableAccrualActivityPosting(false)
                .setNumberOfApprovedInstallments(0)
                .setPaidInAdvance(null)
                .setChargeOffReasonId(null)
                .setChargeOffReason(null)
                .setChargeOffDate(null)
                .setRecoveryRepaymentProcessingCode(null)
                .setRecoveryRepaymentProcessingDescription(null)
                .setNextScheduleDate(null)
                .setChargedOffBy(null)
                .setChargedOffOn(null)
                .setChargedOffNote(null)
                .setWriteOffDate(null)
                .setWrittenOffBy(null)
                .setWrittenOffOn(null)
                .setWrittenOffNote(null)
                .setRescheduledBy(null)
                .setRescheduledOn(null)
                .setRescheduledNote(null)
                .setClosedOnDate(null)
                .setClosedByUsername(null)
                .setClosedNote(null)
                .setRejectedOnDate(null)
                .setRejectedByUsername(null)
                .setRejectedNote(null)
                .setWithdrawnOnDate(null)
                .setWithdrawnByUsername(null)
                .setWithdrawnNote(null)
                .setDisbursedByUsername(null)
                .setDisbursedNote(null)
                .setExpectedOnTimePrincipalPayment(BigDecimal.ZERO)
                .setExpectedOnTimeInterestPayment(BigDecimal.ZERO)
                .setPaidOnTimePrincipalPayment(BigDecimal.ZERO)
                .setPaidOnTimeInterestPayment(BigDecimal.ZERO)
                .setIsNpa(false)
                .setAccruedInterest(BigDecimal.ZERO)
                .setAccruedFee(BigDecimal.ZERO)
                .setAccruedPenalty(BigDecimal.ZERO)
                .setLastRepaymentDate(null)
                .setLastRepaymentAmount(null)
                .setLastClosedBusinessDate(null)
                .setOverduePrincipal(BigDecimal.ZERO)
                .setOverdueInterest(BigDecimal.ZERO)
                .setOverdueFee(BigDecimal.ZERO)
                .setOverduePenalty(BigDecimal.ZERO)
                .setOverdueSinceDate(null)
                .setTotalOverdueAmount(BigDecimal.ZERO)
                .setTotalOverdueLateCharges(BigDecimal.ZERO)
                .setLastCalculatedAccrualDate(null)
                .setLastCalculatedInterestDate(null)
                .setLastCalculatedPenaltyDate(null)
                .setLastCalculatedFeeDate(null)
                .setLastReprocessDate(null)
                .setLastReprocessBusinessDate(null)
                .setAccrualSuspended(false)
                .setAccrualSuspendedOn(null)
                .setAccrualSuspendedBy(null)
                .setAccrualSuspendedReason(null)
                .setAccrualResumedOn(null)
                .setAccrualResumedBy(null)
                .setAccrualResumedReason(null)
                .setInterestRateDifferential(null)
                .setEffectiveInterestRate(null)
                .setLastPaymentAppliedDate(null)
                .setLastPaymentAppliedAmount(null)
                .setLastPaymentAppliedTransactionId(null)
                .setLastPaymentAppliedTransactionType(null)
                .build();
    }

    @Test
    void onLoanCreated_shouldCreateNewAggregator_whenNoneExists() {
        Long loanId = 1L;
        String tenantId = "test-tenant";
        // Status 100: Submitted and pending approval (maps to PENDING_APPROVAL in LoanStatus enum)
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, tenantId, 100);

        StepVerifier.create(aggregatorService.onLoanCreated(loanData))
                .assertNext(aggregator -> {
                    assertThat(aggregator).isNotNull();
                    assertThat(aggregator.getId()).isNotNull();
                    assertThat(aggregator.getLoanId()).isEqualTo(loanId);
                    assertThat(aggregator.getTenantId()).isEqualTo(tenantId);
                    assertThat(aggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
                    assertThat(aggregator.getBankStmtUploaded()).isFalse();
                    assertThat(aggregator.getIdDocUploaded()).isFalse();
                    assertThat(aggregator.getKycDocUploaded()).isFalse();
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING);
                    assertThat(aggregator.getRiskGrade()).isEqualTo("UNKNOWN");
                })
                .verifyComplete();

        // Verify it's actually in the DB
        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void onLoanCreated_shouldNotCreateNewAggregator_whenOneAlreadyExists() {
        Long loanId = 2L;
        String tenantId = "test-tenant-2";
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, tenantId, 100);

        // Create one first
        aggregatorService.onLoanCreated(loanData).block();

        // Try to create again
        StepVerifier.create(aggregatorService.onLoanCreated(loanData))
                .verifyComplete(); // Expect empty mono because it already exists

        // Verify only one exists in the DB using countByLoanId
        StepVerifier.create(aggregatorRepository.countByLoanId(loanId))
             .expectNext(1L)
             .verifyComplete();
    }

    @Test
    void onLoanUpdated_shouldUpdateExistingAggregator() {
        Long loanId = 3L;
        String tenantIdInitial = "tenant-initial-3";
        LoanAccountDataV1 initialLoanData = createTestLoanAccountDataV1(loanId, tenantIdInitial, 100); // Status: PENDING_APPROVAL

        // Create initial aggregator
        Aggregator initialAggregator = aggregatorService.onLoanCreated(initialLoanData).block();
        assertThat(initialAggregator).isNotNull();
        assertThat(initialAggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);

        // New loan data for update - e.g., loan is now Active (status 300)
        String tenantIdUpdated = "tenant-updated-3"; // Assuming tenantId might change, though less common for updates
        LoanAccountDataV1 updatedLoanData = createTestLoanAccountDataV1(loanId, tenantIdUpdated, 300); // Status: ACTIVE

        StepVerifier.create(aggregatorService.onLoanUpdated(updatedLoanData))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(updatedAggregator -> {
                    assertThat(updatedAggregator).isNotNull();
                    assertThat(updatedAggregator.getId()).isEqualTo(initialAggregator.getId());
                    assertThat(updatedAggregator.getLoanId()).isEqualTo(loanId);
                    assertThat(updatedAggregator.getTenantId()).isEqualTo(tenantIdUpdated); // Check if tenantId is updated
                    assertThat(updatedAggregator.getLoanStatus()).isEqualTo(org.mifos.loanrisk.common.LoanStatus.ACTIVE);
                    assertThat(updatedAggregator.getLastUpdated()).isAfter(initialAggregator.getLastUpdated());
                })
                .verifyComplete();
    }

    @Test
    void onLoanUpdated_shouldFail_whenAggregatorDoesNotExist() {
        Long loanId = 4L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-4", 300);

        StepVerifier.create(aggregatorService.onLoanUpdated(loanData))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void onLoanWithdrawn_shouldCancelAggregator() {
        Long loanId = 5L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-5", 100); // Initial state

        Aggregator initialAggregator = aggregatorService.onLoanCreated(loanData).block();
        assertThat(initialAggregator).isNotNull();
        assertThat(initialAggregator.getAssessmentStatus()).isNotEqualTo(org.mifos.loanrisk.common.ServiceStatus.CANCELLED);

        // Simulate loan withdrawal - the status in LoanAccountDataV1 might be 'WITHDRAWN' (e.g. 400)
        // For this test, we directly call onLoanWithdrawn, which uses the loanId from the input loanData.
        LoanAccountDataV1 withdrawnLoanData = createTestLoanAccountDataV1(loanId, "tenant-5", 400); // Status: WITHDRAWN (example)

        StepVerifier.create(aggregatorService.onLoanWithdrawn(withdrawnLoanData))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.CANCELLED);
                    assertThat(aggregator.getLastUpdated()).isAfter(initialAggregator.getLastUpdated());
                })
                .verifyComplete();
    }

    @Test
    void onLoanWithdrawn_shouldFail_whenAggregatorDoesNotExist() {
        Long loanId = 6L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-6", 400);

        StepVerifier.create(aggregatorService.onLoanWithdrawn(loanData))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    void onLoanRejected_shouldDeleteAggregator() {
        Long loanId = 7L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-7", 100); // Initial state

        aggregatorService.onLoanCreated(loanData).block(); // Ensure aggregator exists

        // Verify it exists
        StepVerifier.create(aggregatorRepository.existsByLoanId(loanId))
                .expectNext(true)
                .verifyComplete();

        // Simulate loan rejection - status in LoanAccountDataV1 might be 'REJECTED' (e.g. 500)
        LoanAccountDataV1 rejectedLoanData = createTestLoanAccountDataV1(loanId, "tenant-7", 500);

        StepVerifier.create(aggregatorService.onLoanRejected(rejectedLoanData))
                .verifyComplete();

        // Verify it's deleted
        StepVerifier.create(aggregatorRepository.existsByLoanId(loanId))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void onLoanRejected_shouldFail_whenAggregatorDoesNotExist() {
        // The current implementation of onLoanRejected uses switchIfEmpty which would error.
        // If the desired behavior is to complete silently, the service method needs adjustment.
        // Testing current behavior:
        Long loanId = 8L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-8", 500);

        StepVerifier.create(aggregatorService.onLoanRejected(loanData))
                .expectError(IllegalStateException.class)
                .verify();
    }

    // Helper to create DocumentDataV1 for testing
    private DocumentDataV1 createTestDocumentDataV1(Long docId, Long loanId, String parentEntityType, String docName, String fileName) {
        return DocumentDataV1.newBuilder()
                .setId(docId)
                .setParentEntityId(loanId)
                .setParentEntityType(parentEntityType)
                .setName(docName) // This will be parsed by DocumentType.of(docName)
                .setFileName(fileName)
                .setSize(1024L)
                .setType("application/pdf")
                .setDescription("Test document description")
                // .setLocation("test/location/" + fileName) // Removed: Method does not exist on builder
                .setStorageType(0) // Inline, S3 etc. - assuming 0 for some default
                // .setCreatedByUsername("test_user") // Temporarily removed
                // .setCreatedOn(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                // .setLastModifiedBy("test_user") // Temporarily removed
                // .setLastModifiedOn(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) // Temporarily removed
                .build();
    }

    @Test
    void onDocumentCreated_shouldUpdateAggregatorFlags_forKnownDocumentType() {
        Long loanId = 9L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-9", 100);
        Aggregator initialAggregator = aggregatorService.onLoanCreated(loanData).block();
        assertThat(initialAggregator).isNotNull();
        assertThat(initialAggregator.getBankStmtUploaded()).isFalse();
        assertThat(initialAggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING);

        // Document: Bank Statement Uploaded (assuming "Bank Statement" is a valid name for DocumentType.BANK_STATEMENT)
        DocumentDataV1 docData = createTestDocumentDataV1(101L, loanId, "loan", "Bank Statement", "bank_statement.pdf");

        StepVerifier.create(aggregatorService.onDocumentCreated(docData))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isTrue();
                    assertThat(aggregator.getLastUpdated()).isAfter(initialAggregator.getLastUpdated());
                    // Assessment status might change if all required docs are now present.
                    // Current reevaluateStatus logic in Aggregator.java:
                    // PENDING -> REQUESTED if bank_stmt_uploaded && id_doc_uploaded && kyc_doc_uploaded
                    // So, if only bank_stmt_uploaded is true, it should remain PENDING or become REQUESTED if others were already true.
                    // For this specific test, assuming others are false, it remains PENDING.
                    // To test REQUESTED state, we'd need to upload all required docs.
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING);
                })
                .verifyComplete();
    }

    @Test
    void onDocumentCreated_shouldUpdateAssessmentStatusToRequested_whenAllDocumentsAreUploaded() {
        Long loanId = 10L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-10", 100);
        aggregatorService.onLoanCreated(loanData).block();

        // Upload ID Doc
        DocumentDataV1 idDoc = createTestDocumentDataV1(201L, loanId, "loan", "Identification Document", "id.pdf");
        aggregatorService.onDocumentCreated(idDoc).block();
         // Upload KYC Doc
        DocumentDataV1 kycDoc = createTestDocumentDataV1(202L, loanId, "loan", "KYC Document", "kyc.pdf");
        aggregatorService.onDocumentCreated(kycDoc).block();

        // Verify still PENDING before final doc
        Aggregator aggregatorBeforeFinalDoc = aggregatorRepository.findByLoanId(loanId).block();
        assertThat(aggregatorBeforeFinalDoc.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING);
        assertThat(aggregatorBeforeFinalDoc.getIdDocUploaded()).isTrue();
        assertThat(aggregatorBeforeFinalDoc.getKycDocUploaded()).isTrue();
        assertThat(aggregatorBeforeFinalDoc.getBankStmtUploaded()).isFalse();


        // Upload final Bank Statement
        DocumentDataV1 bankStmtDoc = createTestDocumentDataV1(203L, loanId, "loan", "Bank Statement", "bank_statement.pdf");
        StepVerifier.create(aggregatorService.onDocumentCreated(bankStmtDoc))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isTrue();
                    assertThat(aggregator.getIdDocUploaded()).isTrue();
                    assertThat(aggregator.getKycDocUploaded()).isTrue();
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.REQUESTED);
                })
                .verifyComplete();
    }


    @Test
    void onDocumentCreated_shouldSkip_whenParentEntityTypeIsNotLoan() {
        Long loanId = 11L; // This ID won't be used by the service if type is not 'loan'
        DocumentDataV1 docData = createTestDocumentDataV1(102L, loanId, "client", "Client Photo", "client_photo.jpg");

        // No aggregator needs to exist for this test, as it should be skipped before DB lookup
        StepVerifier.create(aggregatorService.onDocumentCreated(docData))
                .verifyComplete(); // Completes without action or error
    }

    @Test
    void onDocumentCreated_shouldSkip_whenDocumentNameIsUnknown() {
        Long loanId = 12L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-12", 100);
        aggregatorService.onLoanCreated(loanData).block(); // Aggregator must exist

        DocumentDataV1 docData = createTestDocumentDataV1(103L, loanId, "loan", "Unknown Document Type", "unknown.pdf");

        StepVerifier.create(aggregatorService.onDocumentCreated(docData))
                .verifyComplete(); // Completes without changing the aggregator
    }

    @Test
    void onDocumentCreated_shouldFail_whenAggregatorDoesNotExistForLoan() {
        Long loanIdNotExisting = 13L;
        DocumentDataV1 docData = createTestDocumentDataV1(104L, loanIdNotExisting, "loan", "Bank Statement", "bs.pdf");

        StepVerifier.create(aggregatorService.onDocumentCreated(docData))
                .expectError(IllegalStateException.class) // "No Aggregator row for loan..."
                .verify();
    }

    @Test
    void onDocumentDeleted_shouldUpdateAggregatorFlags() {
        Long loanId = 14L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-14", 100);
        aggregatorService.onLoanCreated(loanData).block();

        // Create (upload) a bank statement first
        DocumentDataV1 bankStmt = createTestDocumentDataV1(105L, loanId, "loan", "Bank Statement", "bank_statement.pdf");
        aggregatorService.onDocumentCreated(bankStmt).block();

        Aggregator aggregatorAfterUpload = aggregatorRepository.findByLoanId(loanId).block();
        assertThat(aggregatorAfterUpload.getBankStmtUploaded()).isTrue();

        // Now delete it
        StepVerifier.create(aggregatorService.onDocumentDeleted(bankStmt))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isFalse();
                    assertThat(aggregator.getLastUpdated()).isAfter(aggregatorAfterUpload.getLastUpdated());
                     // Assuming assessment status might revert if it was REQUESTED
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING);
                })
                .verifyComplete();
    }

     @Test
    void onDocumentDeleted_shouldUpdateAssessmentStatusFromRequestedToPending() {
        Long loanId = 15L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-15", 100);
        aggregatorService.onLoanCreated(loanData).block();

        // Upload all documents to make status REQUESTED
        DocumentDataV1 idDoc = createTestDocumentDataV1(301L, loanId, "loan", "Identification Document", "id.pdf");
        aggregatorService.onDocumentCreated(idDoc).block();
        DocumentDataV1 kycDoc = createTestDocumentDataV1(302L, loanId, "loan", "KYC Document", "kyc.pdf");
        aggregatorService.onDocumentCreated(kycDoc).block();
        DocumentDataV1 bankStmtDoc = createTestDocumentDataV1(303L, loanId, "loan", "Bank Statement", "bank_statement.pdf");
        aggregatorService.onDocumentCreated(bankStmtDoc).block();

        Aggregator aggregatorAfterAllUploads = aggregatorRepository.findByLoanId(loanId).block();
        assertThat(aggregatorAfterAllUploads.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.REQUESTED);

        // Delete one document (e.g., Bank Statement)
        StepVerifier.create(aggregatorService.onDocumentDeleted(bankStmtDoc))
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .assertNext(aggregator -> {
                    assertThat(aggregator.getBankStmtUploaded()).isFalse();
                    assertThat(aggregator.getIdDocUploaded()).isTrue(); // Others remain true
                    assertThat(aggregator.getKycDocUploaded()).isTrue();
                    assertThat(aggregator.getAssessmentStatus()).isEqualTo(org.mifos.loanrisk.common.ServiceStatus.PENDING); // Back to PENDING
                })
                .verifyComplete();
    }


    @Test
    void onDocumentDeleted_shouldSkip_whenParentEntityTypeIsNotLoan() {
        DocumentDataV1 docData = createTestDocumentDataV1(106L, 99L, "client", "Client Photo", "client_photo.jpg");
        StepVerifier.create(aggregatorService.onDocumentDeleted(docData))
                .verifyComplete();
    }

    @Test
    void onDocumentDeleted_shouldSkip_whenDocumentNameIsUnknown() {
        Long loanId = 16L;
        LoanAccountDataV1 loanData = createTestLoanAccountDataV1(loanId, "tenant-16", 100);
        aggregatorService.onLoanCreated(loanData).block();
        DocumentDataV1 docData = createTestDocumentDataV1(107L, loanId, "loan", "Mystery File", "mystery.dat");
        StepVerifier.create(aggregatorService.onDocumentDeleted(docData))
                .verifyComplete();
    }

    @Test
    void onDocumentDeleted_shouldFail_whenAggregatorDoesNotExistForLoan() {
        Long loanIdNotExisting = 17L;
        DocumentDataV1 docData = createTestDocumentDataV1(108L, loanIdNotExisting, "loan", "Bank Statement", "bs.pdf");
        StepVerifier.create(aggregatorService.onDocumentDeleted(docData))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
