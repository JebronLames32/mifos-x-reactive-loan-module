package org.mifos.loanrisk.service;

import org.apache.fineract.avro.document.v1.DocumentDataV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.document.common.DocumentType;
import org.mifos.loanrisk.domain.Aggregator;
import org.mifos.loanrisk.repository.AggregatorRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AggregatorServiceTest {

    @Mock
    private AggregatorRepository aggregatorRepositoryMock;

    @InjectMocks
    private AggregatorService aggregatorService;

    private DocumentDataV1 testDocumentDataV1;

    @BeforeEach
    void setUp() {
        testDocumentDataV1 = DocumentDataV1.newBuilder()
                .setId(1L)
                .setParentEntityType("loan")
                .setParentEntityId(100L)
                .setName(DocumentType.BANK_STATEMENT.getDocumentName())
                .setType("application/pdf")
                .setDescription("Monthly bank statement")
                .setStorageReference("ref/123")
                .setCreatedBy("testuser")
                .setCreatedAt(Instant.now().toEpochMilli())
                .setLastModifiedBy("testuser")
                .setLastModifiedAt(Instant.now().toEpochMilli())
                .build();
    }

    @Test
    void onDocumentCreated_whenValidLoanDocument_shouldUpdateAggregator() {
        Aggregator existingAggregator = new Aggregator();
        existingAggregator.setId(50L);
        existingAggregator.setLoanId(100L);
        existingAggregator.setBankStmtUploaded(false); // Initial state

        when(aggregatorRepositoryMock.findByLoanId(100L)).thenReturn(Mono.just(existingAggregator));
        ArgumentCaptor<Aggregator> aggregatorCaptor = ArgumentCaptor.forClass(Aggregator.class);
        // Ensure the save mock returns the captured (and mutated) aggregator
        when(aggregatorRepositoryMock.save(aggregatorCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(aggregatorService.onDocumentCreated(testDocumentDataV1))
                .verifyComplete();

        verify(aggregatorRepositoryMock, times(1)).findByLoanId(100L);
        verify(aggregatorRepositoryMock, times(1)).save(any(Aggregator.class));

        Aggregator savedAggregator = aggregatorCaptor.getValue();
        assertTrue(savedAggregator.isBankStmtUploaded(), "Bank statement flag should be true after BANK_STATEMENT created");
    }

    @Test
    void onDocumentCreated_whenNonLoanDocument_shouldSkipProcessingAndNotCallRepo() {
        testDocumentDataV1.setParentEntityType("client_document"); // Not "loan"

        StepVerifier.create(aggregatorService.onDocumentCreated(testDocumentDataV1))
                .verifyComplete();

        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void onDocumentCreated_whenUnknownDocumentName_shouldSkipProcessingAndNotCallRepo() {
        testDocumentDataV1.setName("very_random_document_name.docx");

        StepVerifier.create(aggregatorService.onDocumentCreated(testDocumentDataV1))
                .verifyComplete();

        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void onDocumentCreated_whenAggregatorNotFoundForLoan_shouldReturnError() {
        when(aggregatorRepositoryMock.findByLoanId(100L)).thenReturn(Mono.empty());

        StepVerifier.create(aggregatorService.onDocumentCreated(testDocumentDataV1))
                .expectError(IllegalStateException.class)
                .verify();

        verify(aggregatorRepositoryMock, times(1)).findByLoanId(100L);
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void onDocumentDeleted_whenValidLoanDocument_shouldUpdateAggregator() {
        Aggregator existingAggregator = new Aggregator();
        existingAggregator.setId(51L);
        existingAggregator.setLoanId(100L);
        existingAggregator.setBankStmtUploaded(true); // Initial state for deletion

        when(aggregatorRepositoryMock.findByLoanId(100L)).thenReturn(Mono.just(existingAggregator));
        ArgumentCaptor<Aggregator> aggregatorCaptor = ArgumentCaptor.forClass(Aggregator.class);
        when(aggregatorRepositoryMock.save(aggregatorCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));


        StepVerifier.create(aggregatorService.onDocumentDeleted(testDocumentDataV1))
                .verifyComplete();

        verify(aggregatorRepositoryMock, times(1)).findByLoanId(100L);
        verify(aggregatorRepositoryMock, times(1)).save(any(Aggregator.class));

        Aggregator savedAggregator = aggregatorCaptor.getValue();
        assertFalse(savedAggregator.isBankStmtUploaded(), "Bank statement flag should be false after BANK_STATEMENT deleted");
    }

    // Add tests for onDocumentDeleted for non-loan, unknown name, aggregator not found, similar to onDocumentCreated
    @Test
    void onDocumentDeleted_whenNonLoanDocument_shouldSkipProcessingAndNotCallRepo() {
        testDocumentDataV1.setParentEntityType("savings_document");

        StepVerifier.create(aggregatorService.onDocumentDeleted(testDocumentDataV1))
                .verifyComplete();
        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void onDocumentDeleted_whenUnknownDocumentName_shouldSkipProcessingAndNotCallRepo() {
        testDocumentDataV1.setName("another_unknown_doc.txt");
        StepVerifier.create(aggregatorService.onDocumentDeleted(testDocumentDataV1))
                .verifyComplete();
        verify(aggregatorRepositoryMock, never()).findByLoanId(anyLong());
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }

    @Test
    void onDocumentDeleted_whenAggregatorNotFoundForLoan_shouldReturnError() {
        when(aggregatorRepositoryMock.findByLoanId(100L)).thenReturn(Mono.empty());
        StepVerifier.create(aggregatorService.onDocumentDeleted(testDocumentDataV1))
                .expectError(IllegalStateException.class)
                .verify();
        verify(aggregatorRepositoryMock, times(1)).findByLoanId(100L);
        verify(aggregatorRepositoryMock, never()).save(any(Aggregator.class));
    }
}
