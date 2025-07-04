package org.mifos.loanrisk.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.common.EventEnvelope;
import org.mifos.loanrisk.document.common.DocumentEventType;
import org.mifos.loanrisk.document.handler.DocumentMessageHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentEventServiceTest {

    @Mock
    private DocumentMessageHandler createdHandlerMock;
    @Mock
    private DocumentMessageHandler deletedHandlerMock;

    private DocumentEventService documentEventService;
    private Map<DocumentEventType, DocumentMessageHandler> handlersMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handlersMap = new HashMap<>();
        handlersMap.put(DocumentEventType.CREATED, createdHandlerMock);
        handlersMap.put(DocumentEventType.DELETED, deletedHandlerMock);
        documentEventService = new DocumentEventService(handlersMap);
    }

    private EventEnvelope<JsonNode> createTestEnvelope(String eventType, String jsonData) throws IOException {
        EventEnvelope<JsonNode> envelope = new EventEnvelope<>();
        envelope.setType(eventType);
        if (jsonData != null) {
            envelope.setPayload(objectMapper.readTree(jsonData));
        }
        return envelope;
    }

    @Test
    void handle_whenCreatedEventType_shouldCallCreatedHandler() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope(DocumentEventType.CREATED.name(), "{\"id\":1}");

        documentEventService.handle(envelope);

        verify(createdHandlerMock, times(1)).handle(envelope.getPayload());
        verify(deletedHandlerMock, never()).handle(any());
    }

    @Test
    void handle_whenDeletedEventType_shouldCallDeletedHandler() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope(DocumentEventType.DELETED.name(), "{\"id\":2}");

        documentEventService.handle(envelope);

        verify(deletedHandlerMock, times(1)).handle(envelope.getPayload());
        verify(createdHandlerMock, never()).handle(any());
    }

    @Test
    void handle_whenEventTypeStringIsInvalidEnum_shouldThrowIllegalArgumentException() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope("INVALID_TYPE_STRING", "{\"id\":3}");

        assertThrows(IllegalArgumentException.class, () -> {
            documentEventService.handle(envelope);
        });
        verifyNoInteractions(createdHandlerMock, deletedHandlerMock);
    }

    @Test
    void handle_whenValidEventTypeHasNoRegisteredHandler_shouldThrowNullPointerException() throws IOException {
        // Setup service with a map missing one of the handlers
        Map<DocumentEventType, DocumentMessageHandler> incompleteMap = new HashMap<>();
        incompleteMap.put(DocumentEventType.CREATED, createdHandlerMock); // DELETED handler is missing
        DocumentEventService serviceWithMissingHandler = new DocumentEventService(incompleteMap);

        EventEnvelope<JsonNode> envelope = createTestEnvelope(DocumentEventType.DELETED.name(), "{\"id\":4}");
        // DELETED is a valid enum, but no handler for it in incompleteMap

        assertThrows(NullPointerException.class, () -> {
            serviceWithMissingHandler.handle(envelope);
        });
        verifyNoInteractions(createdHandlerMock); // createdHandlerMock is in map, but should not be called
    }
}
