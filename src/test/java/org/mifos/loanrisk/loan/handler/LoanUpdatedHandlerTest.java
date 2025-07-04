package org.mifos.loanrisk.loan.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.common.LoanStatus; // Assuming this exists
import org.mifos.loanrisk.domain.LoanSnapshot;
import org.mifos.loanrisk.repository.LoanSnapshotRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// Test class for LoanUpdatedHandler
// Assumes LoanUpdatedHandler will be implemented to:
// 1. Convert JsonNode to LoanAccountDataV1.
// 2. Fetch the existing LoanSnapshot.
// 3. Update the LoanSnapshot with new data (including new Avro payload) and save it.
// 4. Handle errors (conversion, snapshot not found).
@ExtendWith(MockitoExtension.class)
class LoanUpdatedHandlerTest {

    @Mock
    private LoanSnapshotRepository loanSnapshotRepositoryMock;

    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private LoanUpdatedHandler loanUpdatedHandler;

    private LoanAccountDataV1 updatedAvroLoan;
    private JsonNode testJsonNodeInput;
    private LoanSnapshot existingSnapshot;

    @BeforeEach
    void setUp() throws IOException {
        updatedAvroLoan = LoanAccountDataV1.newBuilder()
                .setId(11L) // ID of the loan being updated
                .setAccountNo("LN-011")
                .setPrincipalAmount(BigDecimal.valueOf(12000.00)) // Updated principal
                .setLoanStatus(1) // Example: Active status, map to LoanStatus enum
                // Include other fields that might be part of an update
                .setExternalId(UUID.randomUUID().toString())
                .setClientId(21L)
                .setGroupId(31L)
                .setLoanProductId(41L)
                .setLoanProductName("Updated Product")
                .setSubmittedOnDate(LocalDate.now().minusDays(5).toString())
                .setExpectedDisbursementDate(LocalDate.now().plusDays(2).toString())
                .setCurrencyCode("USD")
                .setNumberOfRepayments(10)
                .setRepaymentEvery(1)
                .setRepaymentFrequencyType(1)
                .setInterestRatePerPeriod(BigDecimal.valueOf(1.2))
                .setAnnualNominalInterestRate(BigDecimal.valueOf(14.40))
                .setInterestFrequencyType(1)
                .setAmortizationType(1)
                .setInterestType(0)
                .setInterestCalculationPeriodType(0)
                .setLoanTermFrequency(10)
                .setLoanTermFrequencyType(1)
                .setLoanType("business")
                .setTransactionProcessingStrategyCode("mifos-standard-strategy")
                .setLoanCycle(2)
                .build();

        testJsonNodeInput = objectMapperSpy.readTree(updatedAvroLoan.toString());

        existingSnapshot = new LoanSnapshot();
        existingSnapshot.setId(1001L); // DB ID of the snapshot record
        existingSnapshot.setLoanId(11L); // Matches updatedAvroLoan.getId()
        existingSnapshot.setAccountNo("LN-011");
        existingSnapshot.setPrincipal(BigDecimal.valueOf(10000.00)); // Original principal
        existingSnapshot.setStatus(LoanStatus.PENDING_APPROVAL.name()); // Original status
        existingSnapshot.setAvroPayload(ByteBuffer.wrap("Old Avro Payload".getBytes()));
    }

    @Test
    void handle_whenValidLoanUpdatedEvent_shouldFetchUpdateAndSaveSnapshot() throws IOException {
        // Arrange
        doReturn(updatedAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(11L)).thenReturn(Mono.just(existingSnapshot));
        when(loanSnapshotRepositoryMock.save(any(LoanSnapshot.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Act
        loanUpdatedHandler.handle(testJsonNodeInput);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        verify(loanSnapshotRepositoryMock, times(1)).findByLoanId(11L);

        ArgumentCaptor<LoanSnapshot> snapshotCaptor = ArgumentCaptor.forClass(LoanSnapshot.class);
        verify(loanSnapshotRepositoryMock, times(1)).save(snapshotCaptor.capture());

        LoanSnapshot capturedSnapshot = snapshotCaptor.getValue();
        assertNotNull(capturedSnapshot);
        assertEquals(existingSnapshot.getId(), capturedSnapshot.getId()); // Should be an update, so ID is same
        assertEquals(updatedAvroLoan.getId(), capturedSnapshot.getLoanId());
        // Verify fields were updated
        assertEquals(0, updatedAvroLoan.getPrincipalAmount().compareTo(capturedSnapshot.getPrincipal()));
        // Assuming LoanAccountDataV1.getLoanStatus() maps to a string name for LoanStatus
        // assertEquals(LoanStatus.ACTIVE.name(), capturedSnapshot.getStatus()); // Or map updatedAvroLoan.getLoanStatus()

        // Verify the entire Avro payload was updated in the snapshot
        assertArrayEquals(updatedAvroLoan.toByteBuffer().array(), capturedSnapshot.getAvroPayload().array());
    }

    @Test
    void handle_whenJsonConversionFails_shouldNotProceed() throws IOException {
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"corrupted\": true}");
        when(objectMapperSpy.treeToValue(malformedJsonNode, LoanAccountDataV1.class))
            .thenThrow(new IOException("Simulated JSON conversion failure"));

        loanUpdatedHandler.handle(malformedJsonNode);

        verify(objectMapperSpy, times(1)).treeToValue(malformedJsonNode, LoanAccountDataV1.class);
        verifyNoInteractions(loanSnapshotRepositoryMock);
    }

    @Test
    void handle_whenLoanSnapshotNotFound_shouldLogErrorAndNotSave() throws IOException {
        doReturn(updatedAvroLoan).when(objectMapperSpy).treeToValue(testJsonNodeInput, LoanAccountDataV1.class);
        when(loanSnapshotRepositoryMock.findByLoanId(11L)).thenReturn(Mono.empty()); // Simulate not found

        loanUpdatedHandler.handle(testJsonNodeInput);

        verify(loanSnapshotRepositoryMock, times(1)).findByLoanId(11L);
        verify(loanSnapshotRepositoryMock, never()).save(any(LoanSnapshot.class));
        // Ideal: verify logging of "Snapshot not found for loan ID 11"
    }

    // Helper for UUID.randomUUID().toString() in Avro object
    private static class UUID {
        public static java.util.UUID randomUUID() {
            return java.util.UUID.randomUUID();
        }
    }
}
