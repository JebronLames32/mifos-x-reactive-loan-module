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

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Test class for DocumentDeletedHandler
// Assumes DocumentDeletedHandler will be implemented to:
// 1. Use an injected ObjectMapper to convert JsonNode to DocumentDataV1.
// 2. Call aggregatorService.onDocumentDeleted() with the converted DocumentDataV1.
// 3. Handle IOException during conversion gracefully.
@ExtendWith(MockitoExtension.class)
class DocumentDeletedHandlerTest {

    @Mock
    private AggregatorService aggregatorServiceMock;

    @Spy
    private ObjectMapper objectMapperSpy = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private DocumentDeletedHandler documentDeletedHandler;

    private DocumentDataV1 testAvroDocument;
    private JsonNode testJsonNodeInput;

    @BeforeEach
    void setUp() throws IOException {
        testAvroDocument = DocumentDataV1.newBuilder()
                .setId(2L)
                .setParentEntityType("loan")
                .setParentEntityId(101L)
                .setName("IDCard.jpg")
                .setType("image/jpeg")
                .setDescription("ID Card Scan")
                .setStorageReference("ref/doc002")
                .setCreatedBy("testUser2")
                .setCreatedAt(Instant.now().minusSeconds(3600).toEpochMilli())
                .setLastModifiedBy("testUser2")
                .setLastModifiedAt(Instant.now().toEpochMilli())
                .build();

        testJsonNodeInput = objectMapperSpy.readTree(testAvroDocument.toString());
    }

    @Test
    void handle_whenValidJsonNode_shouldConvertAndCallAggregatorServiceOnDocumentDeleted() throws IOException {
        // Arrange
        doReturn(testAvroDocument).when(objectMapperSpy).treeToValue(testJsonNodeInput, DocumentDataV1.class);
        when(aggregatorServiceMock.onDocumentDeleted(any(DocumentDataV1.class))).thenReturn(Mono.empty());

        // Act
        documentDeletedHandler.handle(testJsonNodeInput);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(testJsonNodeInput, DocumentDataV1.class);

        ArgumentCaptor<DocumentDataV1> captor = ArgumentCaptor.forClass(DocumentDataV1.class);
        verify(aggregatorServiceMock, times(1)).onDocumentDeleted(captor.capture());

        DocumentDataV1 capturedDocument = captor.getValue();
        assertEquals(testAvroDocument.getId(), capturedDocument.getId());
        assertEquals(testAvroDocument.getParentEntityId(), capturedDocument.getParentEntityId());
        assertEquals(testAvroDocument.getName(), capturedDocument.getName());
    }

    @Test
    void handle_whenJsonNodeConversionFails_shouldLogErrorAndNotCallAggregatorService() throws IOException {
        // Arrange
        JsonNode malformedJsonNode = objectMapperSpy.readTree("{\"corrupted\": false}");
        when(objectMapperSpy.treeToValue(malformedJsonNode, DocumentDataV1.class))
            .thenThrow(new IOException("Simulated JSON conversion failure for delete handler"));

        // Act
        documentDeletedHandler.handle(malformedJsonNode);

        // Assert
        verify(objectMapperSpy, times(1)).treeToValue(malformedJsonNode, DocumentDataV1.class);
        verify(aggregatorServiceMock, never()).onDocumentDeleted(any(DocumentDataV1.class));
        // Ideal: Verify error logging.
    }
}
