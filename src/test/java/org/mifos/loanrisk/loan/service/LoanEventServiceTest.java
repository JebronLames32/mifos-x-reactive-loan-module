package org.mifos.loanrisk.loan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.common.EventEnvelope;
import org.mifos.loanrisk.loan.common.LoanEventType;
import org.mifos.loanrisk.loan.handler.LoanMessageHandler;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanEventServiceTest {

    @Mock
    private LoanMessageHandler createdHandlerMock;
    @Mock
    private LoanMessageHandler updatedHandlerMock;
    @Mock
    private LoanMessageHandler rejectedHandlerMock;
    @Mock
    private LoanMessageHandler withdrawnHandlerMock;

    private LoanEventService loanEventService;
    private Map<LoanEventType, LoanMessageHandler> handlersMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handlersMap = new HashMap<>();
        handlersMap.put(LoanEventType.CREATED, createdHandlerMock);
        handlersMap.put(LoanEventType.UPDATED, updatedHandlerMock);
        handlersMap.put(LoanEventType.REJECTED, rejectedHandlerMock);
        handlersMap.put(LoanEventType.WITHDRAWN, withdrawnHandlerMock);
        loanEventService = new LoanEventService(handlersMap);
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
        EventEnvelope<JsonNode> envelope = createTestEnvelope(LoanEventType.CREATED.name(), "{\"loanId\":1}");
        loanEventService.handle(envelope);
        verify(createdHandlerMock, times(1)).handle(envelope.getPayload());
        verifyNoInteractions(updatedHandlerMock, rejectedHandlerMock, withdrawnHandlerMock);
    }

    @Test
    void handle_whenUpdatedEventType_shouldCallUpdatedHandler() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope(LoanEventType.UPDATED.name(), "{\"loanId\":2}");
        loanEventService.handle(envelope);
        verify(updatedHandlerMock, times(1)).handle(envelope.getPayload());
        verifyNoInteractions(createdHandlerMock, rejectedHandlerMock, withdrawnHandlerMock);
    }

    @Test
    void handle_whenRejectedEventType_shouldCallRejectedHandler() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope(LoanEventType.REJECTED.name(), "{\"loanId\":3}");
        loanEventService.handle(envelope);
        verify(rejectedHandlerMock, times(1)).handle(envelope.getPayload());
        verifyNoInteractions(createdHandlerMock, updatedHandlerMock, withdrawnHandlerMock);
    }

    @Test
    void handle_whenWithdrawnEventType_shouldCallWithdrawnHandler() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope(LoanEventType.WITHDRAWN.name(), "{\"loanId\":4}");
        loanEventService.handle(envelope);
        verify(withdrawnHandlerMock, times(1)).handle(envelope.getPayload());
        verifyNoInteractions(createdHandlerMock, updatedHandlerMock, rejectedHandlerMock);
    }

    @Test
    void handle_whenEventTypeStringIsInvalidEnum_shouldThrowIllegalArgumentException() throws IOException {
        EventEnvelope<JsonNode> envelope = createTestEnvelope("NON_EXISTENT_LOAN_TYPE", "{\"id\":5}");
        assertThrows(IllegalArgumentException.class, () -> loanEventService.handle(envelope));
        verifyNoInteractions(createdHandlerMock, updatedHandlerMock, rejectedHandlerMock, withdrawnHandlerMock);
    }

    @Test
    void handle_whenValidEventTypeHasNoRegisteredHandler_shouldThrowNullPointerException() throws IOException {
        Map<LoanEventType, LoanMessageHandler> incompleteMap = new HashMap<>();
        incompleteMap.put(LoanEventType.CREATED, createdHandlerMock);
        // UPDATED handler is missing from this specific map instance
        LoanEventService serviceWithMissingHandler = new LoanEventService(incompleteMap);

        EventEnvelope<JsonNode> envelope = createTestEnvelope(LoanEventType.UPDATED.name(), "{\"id\":6}");
        // UPDATED is a valid enum type, but no handler for it in incompleteMap

        assertThrows(NullPointerException.class, () -> serviceWithMissingHandler.handle(envelope));
        verifyNoInteractions(createdHandlerMock); // createdHandlerMock is in map, but should not be called
    }
}
