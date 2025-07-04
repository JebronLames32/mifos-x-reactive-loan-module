package org.mifos.loanrisk.loan.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.common.LoanStatus; // Ensure this enum/class exists
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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Test class for LoanRejectedHandler
// Assumes LoanRejectedHandler will:
// 1. Convert JsonNode to LoanAccountDataV1.
// 2. Fetch and update LoanSnapshot status to REJECTED.
// 3. Fetch and update Aggregator assessmentStatus to reflect rejection.
// 4. Handle errors.
@ExtendWith(MockitoExtension.class)
class LoanRejectedHandlerTest {

    @Mock
    private LoanSnapshotRepository loanSnapshotRepositoryMock;
    @Mock
    private AggregatorRepository aggregatorRepositoryMock;

    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private LoanRejectedHandler loanRejectedHandler;

    private LoanAccountDataV1 rejectedAvroLoan;
    private JsonNode testJsonNodeInput;
    private LoanSnapshot existingSnapshot;
    private Aggregator existingAggregator;

    @BeforeEach
    void setUp() throws IOException {
        rejectedAvroLoan = LoanAccountDataV1.newBuilder()
                .setId(12L) // ID of the loan being rejected
                .setAccountNo("LN-012")
                .setLoanStatus(LoanStatus.REJECTED.ordinal()) // Assuming Avro uses ordinal or specific int for status
                                                              // Or that this field is primarily for information and handler sets its own status
                .setPrincipalAmount(BigDecimal.valueOf(7500.00))
                 // Include other necessary fields from LoanAccountDataV1 schema for completeness
                .setExternalId(UUID.randomUUID().toString())
                .setClientId(22L).setLoanProductId(42L).setLoanProductName("Rejected Product")
                .setSubmittedOnDate(LocalDate.now().minusDays(2).toString()).setCurrencyCode("USD")
                .build();

        testJsonNodeInput = objectMapperSpy.readTree(rejectedAvroLoan.toString());

        existingSnapshot = new LoanSnapshot();
        existingSnapshot.setId(1002L);
        existingSnapshot.setLoanId(12L);
        existingSnapshot.setStatus(LoanStatus.PENDING_APPROVAL.name()); // Initial status

        existingAggregator = new Aggregator();
        existingAggregator.setId(2002L);
        existingAggregator.setLoanId(12L);
        existingAggregator.setAssessmentStatus(LoanStatus.PENDING_APPROVAL.name()); // Initial status
    }

    @Test
    void handle_whenValidLoanRejectedEvent_shouldUpdateSnapshotAndAggregatorStatus() throws IOException {
        // Arrange
        doReturn(rejectedAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        when(loanSnapshotRepositoryMock.findByLoanId(12L)).thenReturn(Mono.just(existingSnapshot));
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(aggregatorRepositoryMock.findByLoanId(12L)).thenReturn(Mono.just(existingAggregator));
        when(aggregatorRepositoryMock.save(any(Aggregator.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Act
        loanRejectedHandler.handle(testJsonNodeInput);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());
        assertEquals(LoanStatus.REJECTED.name(), snapshotCaptor.getValue().getStatus());

        ArgumentCaptor<Aggregator> aggregatorCaptor = ArgumentCaptor.forClass(Aggregator.class);
        verify(aggregatorRepositoryMock, times(1)).save(aggregatorCaptor.capture());
        // Assuming REJECTED maps to a specific assessmentStatus, e.g., "REJECTED" or "CLOSED_REJECTED"
        assertEquals(LoanStatus.REJECTED.name(), aggregatorCaptor.getValue().getAssessmentStatus());
    }

    @Test
    void handle_whenJsonConversionFails_shouldNotProceed() throws IOException {
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"error\": true}");
        when(objectMapperSpy.treeToValue(malformedJsonNode, LoanAccountDataV1.class))
            .thenThrow(new IOException("Conversion fail"));

        loanRejectedHandler.handle(malformedJsonNode);

        verifyNoInteractions(loanSnapshotRepositoryMock, aggregatorRepositoryMock);
    }

    @Test
    void handle_whenLoanSnapshotNotFound_shouldNotUpdateAggregator() throws IOException {
        doReturn(rejectedAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(12L)).thenReturn(Mono.empty());

        loanRejectedHandler.handle(testJsonNodeInput);

        verify(loanSnapshotRepositoryMock, times(1)).findByLoanId(12L);
        verify(loanSnapshotRepositoryMock, never()).save(any(LoanSnapshot.class));
        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void handle_whenAggregatorNotFound_shouldStillUpdateSnapshotStatus() throws IOException {
        doReturn(rejectedAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(12L)).thenReturn(Mono.just(existingSnapshot));
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(aggregatorRepositoryMock.findByLoanId(12L)).thenReturn(Mono.empty()); // Aggregator not found

        loanRejectedHandler.handle(testJsonNodeInput);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());
        assertEquals(LoanStatus.REJECTED.name(), snapshotCaptor.getValue().getStatus()); // Snapshot status still updated

        verify(aggregatorRepositoryMock, times(1)).findByLoanId(12L);
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class)); // No aggregator to save
        // Ideal: verify logging for "Aggregator not found for loan ID 12"
    }

    // Helper for UUID.randomUUID().toString() in Avro object
    private static class UUID {
        public static java.util.UUID randomUUID() {
            return java.util.UUID.randomUUID();
        }
    }
}
