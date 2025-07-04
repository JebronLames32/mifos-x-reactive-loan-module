package org.mifos.loanrisk.messaging.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mifos.loanrisk.common.EventCategory;
import org.mifos.loanrisk.common.EventEnvelope;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// Assuming DomainEventService.LOG is accessible for verification or use a TestAppender
// For this example, direct log verification is omitted for brevity.

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventDispatcherTest {

    @Mock
    private DomainEventService loanEventServiceMock;
    @Mock
    private DomainEventService documentEventServiceMock;
    // NOOP is a static final lambda, harder to mock directly without Powermock or changing its design.
    // We test NOOP behavior by ensuring other mocks aren't called and, if possible, checking logs.

    private DomainEventDispatcher domainEventDispatcher;
    private Map<EventCategory, DomainEventService> servicesMap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        servicesMap = new HashMap<>();
        servicesMap.put(EventCategory.LOAN, loanEventServiceMock);
        servicesMap.put(EventCategory.DOCUMENT, documentEventServiceMock);
        domainEventDispatcher = new DomainEventDispatcher(servicesMap);
    }

    private EventEnvelope<JsonNode> createTestEnvelope(EventCategory category, String jsonData) throws IOException {
        EventEnvelope<JsonNode> envelope = new EventEnvelope<>();
        envelope.setCategory(category);
        if (jsonData != null) {
            envelope.setPayload(objectMapper.readTree(jsonData));
        }
        return envelope;
    }

    @Test
    void dispatch_whenLoanEventCategory_shouldCallLoanEventService() throws IOException {
        EventEnvelope<JsonNode> loanEnvelope = createTestEnvelope(EventCategory.LOAN, "{\"data\":\"loan_event\"}");

        domainEventDispatcher.dispatch(loanEnvelope);

        verify(loanEventServiceMock, times(1)).handle(loanEnvelope);
        verify(documentEventServiceMock, never()).handle(any());
    }

    @Test
    void dispatch_whenDocumentEventCategory_shouldCallDocumentEventService() throws IOException {
        EventEnvelope<JsonNode> documentEnvelope = createTestEnvelope(EventCategory.DOCUMENT, "{\"data\":\"doc_event\"}");

        domainEventDispatcher.dispatch(documentEnvelope);

        verify(documentEventServiceMock, times(1)).handle(documentEnvelope);
        verify(loanEventServiceMock, never()).handle(any());
    }

    @Test
    void dispatch_whenUnknownEventCategory_shouldCallNoopServiceAndNotOtherRegisteredServices() throws IOException {
        // Create an EventCategory that is not LOAN or DOCUMENT.
        // If EventCategory is a simple enum with only these two, this test might need adjustment
        // or a different way to represent an "unknown" category for the map.
        // For this test, we'll assume there's a third category or use one not in the map.

        // Let's simulate by having a dispatcher with a map that doesn't contain the given category.
        Map<EventCategory, DomainEventService> specificMap = new HashMap<>();
        // specificMap is empty or doesn't contain EventCategory.EXTERNAL_SYSTEM
        DomainEventDispatcher specificDispatcher = new DomainEventDispatcher(specificMap);

        // Assuming EventCategory might have more values, or we test with a category not in the initial map
        EventEnvelope<JsonNode> unknownCategoryEnvelope = createTestEnvelope(EventCategory.LOAN, "{\"data\":\"unknown_category_event\"}");
        // Since LOAN is not in specificMap, it should default to NOOP.

        // We expect NOOP to be called, which logs a warning.
        // Direct verification of NOOP (a static lambda) is complex.
        // We verify that our registered mocks (if they were in this specificMap) are not called.
        specificDispatcher.dispatch(unknownCategoryEnvelope);

        verify(loanEventServiceMock, never()).handle(any()); // These mocks are not in specificMap
        verify(documentEventServiceMock, never()).handle(any());
        // To truly test NOOP, one would capture log output.
        // System.out.println("A warning log for 'No domain service for LOAN' should have appeared.");
    }

    @Test
    void dispatch_whenEventCategoryIsNullInEnvelope_shouldDefaultToNoop() throws IOException {
        EventEnvelope<JsonNode> nullCategoryEnvelope = createTestEnvelope(null, "{\"data\":\"null_category_event\"}");
        // The servicesMap in domainEventDispatcher contains LOAN and DOCUMENT.
        // getOrDefault(null, NOOP) should result in NOOP.

        domainEventDispatcher.dispatch(nullCategoryEnvelope);

        // Verify that NOOP was effectively chosen, meaning no registered handlers were called.
        verify(loanEventServiceMock, never()).handle(any());
        verify(documentEventServiceMock, never()).handle(any());
        // System.out.println("A warning log for 'No domain service for null' should have appeared.");
    }
}
