package org.mifos.loanrisk.loan.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.domain.Aggregator;
import org.mifos.loanrisk.domain.LoanSnapshot;
import org.mifos.loanrisk.repository.AggregatorRepository;
import org.mifos.loanrisk.repository.LoanSnapshotRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer; // For LoanSnapshot.avroPayload
import java.time.LocalDate; // For LoanSnapshot date fields

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Test class for LoanCreatedHandler
// Assumes LoanCreatedHandler will be implemented to:
// 1. Use an injected ObjectMapper to convert JsonNode to LoanAccountDataV1.
// 2. Create and save a new LoanSnapshot via LoanSnapshotRepository.
// 3. Create and save a new Aggregator via AggregatorRepository.
// 4. Handle errors gracefully (conversion, repository operations).
@ExtendWith(MockitoExtension.class)
class LoanCreatedHandlerTest {

    @Mock
    private LoanSnapshotRepository loanSnapshotRepositoryMock;
    @Mock
    private AggregatorRepository aggregatorRepositoryMock;

    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private LoanCreatedHandler loanCreatedHandler;

    private LoanAccountDataV1 testAvroLoan;
    private JsonNode testJsonNodeInput;

    @BeforeEach
    void setUp() throws IOException {
        testAvroLoan = LoanAccountDataV1.newBuilder()
                .setId(10L)
                .setAccountNo("LN-010")
                .setExternalId(UUID.randomUUID().toString())
                .setClientId(20L)
                .setGroupId(30L)
                .setLoanProductId(40L)
                .setLoanProductName("Test Product")
                .setSubmittedOnDate(LocalDate.now().toString()) // Avro schema might expect String
                .setExpectedDisbursementDate(LocalDate.now().plusDays(5).toString())
                .setPrincipalAmount(BigDecimal.valueOf(10000.00))
                .setCurrencyCode("USD")
                .setNumberOfRepayments(12)
                .setRepaymentEvery(1)
                .setRepaymentFrequencyType(1) // e.g., 1 for Months
                .setInterestRatePerPeriod(BigDecimal.valueOf(1.5))
                .setAnnualNominalInterestRate(BigDecimal.valueOf(18.00))
                .setInterestFrequencyType(1)
                .setAmortizationType(1) // e.g., Equal Installments
                .setInterestType(0) // e.g., Declining Balance
                .setInterestCalculationPeriodType(0) // e.g., Daily
                .setLoanTermFrequency(12)
                .setLoanTermFrequencyType(1)
                .setLoanStatus(0) // Example status, map to your LoanStatus enum if needed
                .setLoanType("individual")
                .setTransactionProcessingStrategyCode("mifos-standard-strategy")
                .setLoanCycle(1)
                .build();

        testJsonNodeInput = objectMapperSpy.readTree(testAvroLoan.toString());
    }

    @Test
    void handle_whenValidLoanCreatedEvent_shouldConvertAndSaveSnapshotAndAggregator() throws IOException {
        // Arrange
        doReturn(testAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        // Mock repository save calls to return the object passed to them
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(aggregatorRepositoryMock.save(any(Aggregator.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Act
        loanCreatedHandler.handle(testJsonNodeInput);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());
        LoanSnapshot capturedSnapshot = snapshotCaptor.getValue();
        assertNotNull(capturedSnapshot);
        assertEquals(testAvroLoan.getId(), capturedSnapshot.getLoanId());
        assertEquals(testAvroLoan.getAccountNo(), capturedSnapshot.getAccountNo());
        // Ensure avroPayload in snapshot matches the original LoanAccountDataV1's byte representation
        assertArrayEquals(testAvroLoan.toByteBuffer().array(), capturedSnapshot.getAvroPayload().array());


        ArgumentCaptor<Aggregator> aggregatorCaptor = ArgumentCaptor.forClass(Aggregator.class);
        verify(aggregatorRepositoryMock, times(1)).save(aggregatorCaptor.capture());
        Aggregator capturedAggregator = aggregatorCaptor.getValue();
        assertNotNull(capturedAggregator);
        assertEquals(testAvroLoan.getId(), capturedAggregator.getLoanId());
        // Assert initial state of aggregator, e.g., status, scores if applicable
        // assertEquals("PENDING_ASSESSMENT", capturedAggregator.getAssessmentStatus());
    }

    @Test
    void handle_whenJsonConversionFails_shouldNotCallRepositories() throws IOException {
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"corrupted\": true}");
        when(objectMapperSpy.treeToValue(malformedJsonNode, LoanAccountDataV1.class))
            .thenThrow(new IOException("Simulated JSON conversion failure"));

        loanCreatedHandler.handle(malformedJsonNode);

        verify(objectMapperSpy, times(1)).treeToValue(malformedJsonNode, LoanAccountDataV1.class);
        verifyNoInteractions(loanSnapshotRepositoryMock, aggregatorRepositoryMock);
    }

    @Test
    void handle_whenLoanSnapshotSaveFails_shouldNotSaveAggregator() throws IOException {
        doReturn(testAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenReturn(Mono.error(new RuntimeException("DB error saving snapshot")));

        // Assuming the handler will catch and log, then not proceed to aggregator save
        loanCreatedHandler.handle(testJsonNodeInput);

        verify(loanSnapshotRepositoryMock, times(1)).save(any(LoanSnapshot.class));
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    // Helper for UUID.randomUUID().toString() in Avro object
    private static class UUID {
        public static java.util.UUID randomUUID() {
            return java.util.UUID.randomUUID();
        }
    }
}
