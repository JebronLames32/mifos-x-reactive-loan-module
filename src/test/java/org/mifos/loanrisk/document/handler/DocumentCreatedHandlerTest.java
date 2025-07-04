package org.mifos.loanrisk.document.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.document.v1.DocumentDataV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.service.AggregatorService;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import org.slf4j.Logger; // For potential log verification

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Test class for DocumentCreatedHandler
// Assumes DocumentCreatedHandler will be implemented to:
// 1. Use an injected ObjectMapper to convert JsonNode to DocumentDataV1.
// 2. Call aggregatorService.onDocumentCreated() with the converted DocumentDataV1.
// 3. Handle IOException during conversion gracefully (e.g., log and not proceed).
@ExtendWith(MockitoExtension.class)
class DocumentCreatedHandlerTest {

    @Mock
    private AggregatorService aggregatorServiceMock;

    // Spy on ObjectMapper to verify its usage and to mock its behavior for error cases.
    // This requires DocumentCreatedHandler to have ObjectMapper injected.
    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private DocumentCreatedHandler documentCreatedHandler;

    private DocumentDataV1 testAvroDocument;
    private JsonNode testJsonNodeInput;

    @BeforeEach
    void setUp() throws IOException {
        testAvroDocument = DocumentDataV1.newBuilder()
                .setId(1L)
                .setParentEntityType("loan")
                .setParentEntityId(100L)
                .setName("BankStatement.pdf")
                .setType("application/pdf")
                .setDescription("Monthly bank statement")
                .setStorageReference("ref/doc001")
                .setCreatedBy("testUser")
                .setCreatedAt(Instant.now().toEpochMilli())
                .setLastModifiedBy("testUser")
                .setLastModifiedAt(Instant.now().toEpochMilli())
                .build();

        // Convert Avro to JsonNode (simulating input to handler)
        // Avro's toString() method produces a JSON representation.
        testJsonNodeInput = objectMapperSpy.readTree(testAvroDocument.toString());
    }

    @Test
    void handle_whenValidJsonNode_shouldConvertAndCallAggregatorService() throws IOException {
        // Arrange
        // Configure the spy to return the specific Avro object when treeToValue is called.
        // This ensures that if the handler calls objectMapper.treeToValue, it gets our test object.
        doReturn(testAvroDocument).when(objectMapperSpy).treeToValue(testJsonNodeInput, DocumentDataV1.class);

        when(aggregatorServiceMock.onDocumentCreated(any(DocumentDataV1.class))).thenReturn(Mono.empty());

        // Act
        documentCreatedHandler.handle(testJsonNodeInput); // The handler is currently a stub, so this won't do much yet.

        // Assert
        // Verify that treeToValue was called on the ObjectMapper spy (part of handler's responsibility)
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, DocumentDataV1.class);

        // Verify that aggregatorService.onDocumentCreated was called with the correct DocumentDataV1 object
        ArgumentCaptor<DocumentDataV1> captor = ArgumentCaptor.forClass(DocumentDataV1.class);
        verify(aggregatorServiceMock, times(1)).onDocumentCreated(captor.capture());

        // Compare fields of the captured object with the original Avro object
        DocumentDataV1 capturedDocument = captor.getValue();
        assertEquals(testAvroDocument.getId(), capturedDocument.getId());
        assertEquals(testAvroDocument.getParentEntityId(), capturedDocument.getParentEntityId());
        assertEquals(testAvroDocument.getName(), capturedDocument.getName());
    }

    @Test
    void handle_whenJsonNodeConversionFails_shouldLogErrorAndNotCallAggregatorService() throws IOException {
        // Arrange
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"malformed\": true}");

        // Configure the spy to throw an IOException when trying to convert this specific malformed node.
        when(objectMapperSpy.treeToValue(malformedJsonNode, DocumentDataV1.class))
            .thenThrow(new IOException("Simulated JSON conversion failure"));

        // Act
        documentCreatedHandler.handle(malformedJsonNode);

        // Assert
        // Verify that treeToValue was attempted (handler's responsibility)
        verify(objectMapperSpy, times(1)).treeToValue(malformedJsonNode, DocumentDataV1.class);

        // Verify that aggregatorService was NOT called due to the conversion error
        verify(aggregatorServiceMock, never()).onDocumentCreated(any(DocumentDataV1.class));

        // Ideal: Verify that an error was logged. This requires a test logger setup.
        // For now, not calling the service implies graceful error handling.
    }
}
