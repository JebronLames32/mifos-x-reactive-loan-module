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

// Test class for LoanWithdrawnHandler
// Assumes LoanWithdrawnHandler will:
// 1. Convert JsonNode to LoanAccountDataV1.
// 2. Fetch and update LoanSnapshot status to WITHDRAWN.
// 3. Fetch and update Aggregator assessmentStatus to reflect withdrawal.
// 4. Handle errors.
@ExtendWith(MockitoExtension.class)
class LoanWithdrawnHandlerTest {

    @Mock
    private LoanSnapshotRepository loanSnapshotRepositoryMock;
    @Mock
    private AggregatorRepository aggregatorRepositoryMock;

    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private LoanWithdrawnHandler loanWithdrawnHandler;

    private LoanAccountDataV1 withdrawnAvroLoan;
    private JsonNode testJsonNodeInput;
    private LoanSnapshot existingSnapshot;
    private Aggregator existingAggregator;

    @BeforeEach
    void setUp() throws IOException {
        withdrawnAvroLoan = LoanAccountDataV1.newBuilder()
                .setId(13L) // ID of the loan being withdrawn
                .setAccountNo("LN-013")
                .setLoanStatus(LoanStatus.WITHDRAWN.ordinal()) // Or appropriate int value
                .setPrincipalAmount(BigDecimal.valueOf(2200.00))
                .setExternalId(UUID.randomUUID().toString())
                .setClientId(23L).setLoanProductId(43L).setLoanProductName("Withdrawn Product")
                .setSubmittedOnDate(LocalDate.now().minusDays(1).toString()).setCurrencyCode("USD")
                .build();

        testJsonNodeInput = objectMapperSpy.readTree(withdrawnAvroLoan.toString());

        existingSnapshot = new LoanSnapshot();
        existingSnapshot.setId(1003L);
        existingSnapshot.setLoanId(13L);
        existingSnapshot.setStatus(LoanStatus.PENDING_APPROVAL.name());

        existingAggregator = new Aggregator();
        existingAggregator.setId(2003L);
        existingAggregator.setLoanId(13L);
        existingAggregator.setAssessmentStatus(LoanStatus.PENDING_APPROVAL.name());
    }

    @Test
    void handle_whenValidLoanWithdrawnEvent_shouldUpdateSnapshotAndAggregatorStatus() throws IOException {
        // Arrange
        doReturn(withdrawnAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        when(loanSnapshotRepositoryMock.findByLoanId(13L)).thenReturn(Mono.just(existingSnapshot));
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(aggregatorRepositoryMock.findByLoanId(13L)).thenReturn(Mono.just(existingAggregator));
        when(aggregatorRepositoryMock.save(any(Aggregator.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Act
        loanWithdrawnHandler.handle(testJsonNodeInput);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());
        assertEquals(LoanStatus.WITHDRAWN.name(), snapshotCaptor.getValue().getStatus());

        ArgumentCaptor<Aggregator> aggregatorCaptor = ArgumentCaptor.forClass(Aggregator.class);
        verify(aggregatorRepositoryMock, times(1)).save(aggregatorCaptor.capture());
        assertEquals(LoanStatus.WITHDRAWN.name(), aggregatorCaptor.getValue().getAssessmentStatus());
    }

    @Test
    void handle_whenJsonConversionFails_shouldNotProceed() throws IOException {
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"invalid_data\": true}");
        when(objectMapperSpy.treeToValue(malformedJsonNode, LoanAccountDataV1.class))
            .thenThrow(new IOException("Conversion error"));

        loanWithdrawnHandler.handle(malformedJsonNode);

        verifyNoInteractions(loanSnapshotRepositoryMock, aggregatorRepositoryMock);
    }

    @Test
    void handle_whenLoanSnapshotNotFound_shouldNotUpdateAggregator() throws IOException {
        doReturn(withdrawnAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(13L)).thenReturn(Mono.empty());

        loanWithdrawnHandler.handle(testJsonNodeInput);

        verify(loanSnapshotRepositoryMock, times(1)).findByLoanId(13L);
        verify(loanSnapshotRepositoryMock, never()).save(any(LoanSnapshot.class));
        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void handle_whenAggregatorNotFound_shouldStillUpdateSnapshotStatus() throws IOException {
        doReturn(withdrawnAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(13L)).thenReturn(Mono.just(existingSnapshot));
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(aggregatorRepositoryMock.findByLoanId(13L)).thenReturn(Mono.empty());

        loanWithdrawnHandler.handle(testJsonNodeInput);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());
        assertEquals(LoanStatus.WITHDRAWN.name(), snapshotCaptor.getValue().getStatus());

        verify(aggregatorRepositoryMock, times(1)).findByLoanId(13L);
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    // Helper for UUID.randomUUID().toString() in Avro object
    private static class UUID {
        public static java.util.UUID randomUUID() {
            return java.util.UUID.randomUUID();
        }
    }
}
