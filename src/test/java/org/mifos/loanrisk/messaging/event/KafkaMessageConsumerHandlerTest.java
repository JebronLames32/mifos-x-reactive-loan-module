package org.mifos.loanrisk.messaging.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.fineract.avro.MessageV1;
import org.apache.fineract.avro.document.v1.DocumentDataV1;
import org.apache.fineract.avro.loan.v1.LoanAccountDataV1;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.common.EventEnvelope;
import org.mifos.loanrisk.common.EventMapper;
import org.mifos.loanrisk.messaging.dispatcher.DomainEventDispatcher;
import org.mifos.loanrisk.messaging.domain.EventMessage;
import org.mifos.loanrisk.messaging.repository.EventMessageRepository;
import org.mifos.loanrisk.utility.ByteBufferConvertor;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = { "${app.kafka.topic}" })
@ActiveProfiles("kafka")
class KafkaMessageConsumerHandlerTest {

    @Autowired
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @SpyBean
    private KafkaMessageConsumerHandler kafkaMessageConsumerHandler;

    @MockBean
    private EventMessageRepository eventMessageRepositoryMock;

    @MockBean
    private DomainEventDispatcher domainEventDispatcherMock;

    // EventMapper is a real bean used by the handler, so autowire it.
    @Autowired
    private EventMapper eventMapper;

    // ByteBufferConvertor is also a real bean used by the handler.
    @Autowired
    private ByteBufferConvertor byteBufferConvertor;

    // ObjectMapper for assertions on JsonNode content
    private final ObjectMapper testObjectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${app.kafka.topic}")
    private String kafkaTopic;

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    // Helper to create Avro MessageV1 for tests
    private MessageV1 createTestAvroMessageV1(String type, String category, String dataSchema, Object data) throws IOException {
        ByteBuffer avroDataBytes = null;
        if (data instanceof LoanAccountDataV1 loanData) {
            avroDataBytes = loanData.toByteBuffer();
        } else if (data instanceof DocumentDataV1 docData) {
            avroDataBytes = docData.toByteBuffer();
        } else {
            throw new IllegalArgumentException("Unsupported Avro data type for testing");
        }

        return MessageV1.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSource("test-kafka-producer")
                .setType(type)
                .setCategory(category)
                .setTenantId("default")
                .setCreatedAt(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC)))
                .setDataContentType("application/avro")
                .setDataSchema(dataSchema) // Important for EventMapper logic
                .setData(avroDataBytes)
                .build();
    }

    @Test
    void handleMessage_whenLoanAccountEvent_shouldSaveAndDispatch() throws Exception {
        LoanAccountDataV1 loanData = LoanAccountDataV1.newBuilder()
                .setId(1L).setAccountNo("LN-001").setLoanCycle(1).setPrincipalAmount(BigDecimal.valueOf(10000.00))
                .build();
        MessageV1 avroMessage = createTestAvroMessageV1("LoanAccountCreatedBusinessEvent", "Loan",
                                                        LoanAccountDataV1.class.getName(), loanData);
        byte[] kafkaPayload = avroMessage.toByteBuffer().array();

        when(eventMessageRepositoryMock.save(any(EventMessage.class))).thenReturn(Mono.just(new EventMessage()));
        doNothing().when(domainEventDispatcherMock).dispatch(any(EventEnvelope.class));

        kafkaTemplate.send(kafkaTopic, kafkaPayload);

        ArgumentCaptor<EventMessage> eventMessageCaptor = ArgumentCaptor.forClass(EventMessage.class);
        verify(eventMessageRepositoryMock, timeout(5000).times(1)).save(eventMessageCaptor.capture());
        assertEquals(avroMessage.getId(), eventMessageCaptor.getValue().getMessageId());
        // Potentially more assertions on EventMessage fields

        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventDispatcherMock, timeout(5000).times(1)).dispatch(envelopeCaptor.capture());

        EventEnvelope<?> capturedEnvelope = envelopeCaptor.getValue();
        assertNotNull(capturedEnvelope);
        assertEquals(avroMessage.getId(), capturedEnvelope.getId());
        assertEquals(avroMessage.getType(), capturedEnvelope.getType());
        assertEquals(avroMessage.getCategory(), capturedEnvelope.getCategory().name()); // Category is enum
        assertNotNull(capturedEnvelope.getPayload()); // Payload is JsonNode
        assertTrue(capturedEnvelope.getPayload() instanceof JsonNode);

        // Verify content of JsonNode by converting it back to LoanAccountDataV1
        LoanAccountDataV1 dispatchedLoanData = testObjectMapper.treeToValue(
            (JsonNode) capturedEnvelope.getPayload(), LoanAccountDataV1.class
        );
        assertEquals(loanData, dispatchedLoanData); // Deep comparison of Avro objects
    }

    @Test
    void handleMessage_whenDocumentEvent_shouldSaveAndDispatch() throws Exception {
        DocumentDataV1 docData = DocumentDataV1.newBuilder()
                .setId(10L).setParentEntityType("loan").setParentEntityId(1L)
                .setName("ID_Card.pdf").setType("application/pdf").setDescription("ID Card Front")
                .setStorageReference("docstore/idcard.pdf").setCreatedBy("user1")
                .setCreatedAt(Instant.now().toEpochMilli()).setLastModifiedBy("user1")
                .setLastModifiedAt(Instant.now().toEpochMilli()).build();
        MessageV1 avroMessage = createTestAvroMessageV1("DocumentCreatedBusinessEvent", "Document",
                                                        DocumentDataV1.class.getName(), docData);
        byte[] kafkaPayload = avroMessage.toByteBuffer().array();

        when(eventMessageRepositoryMock.save(any(EventMessage.class))).thenReturn(Mono.just(new EventMessage()));
        doNothing().when(domainEventDispatcherMock).dispatch(any(EventEnvelope.class));

        kafkaTemplate.send(kafkaTopic, kafkaPayload);

        verify(eventMessageRepositoryMock, timeout(5000).times(1)).save(any(EventMessage.class));
        ArgumentCaptor<EventEnvelope> envelopeCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventDispatcherMock, timeout(5000).times(1)).dispatch(envelopeCaptor.capture());

        EventEnvelope<?> capturedEnvelope = envelopeCaptor.getValue();
        assertNotNull(capturedEnvelope);
        assertEquals(avroMessage.getId(), capturedEnvelope.getId());
        assertTrue(capturedEnvelope.getPayload() instanceof JsonNode);
        DocumentDataV1 dispatchedDocData = testObjectMapper.treeToValue(
            (JsonNode) capturedEnvelope.getPayload(), DocumentDataV1.class
        );
        assertEquals(docData, dispatchedDocData);
    }

    @Test
    void handleMessage_whenAvroProcessingFails_shouldLogErrorAndNotDispatch() throws Exception {
        // Send a payload that is not a valid MessageV1 Avro
        byte[] invalidKafkaPayload = "This is not Avro".getBytes();

        // No mocks for save/dispatch needed as they shouldn't be called.
        // Kafka listener will try to process, log error, and then stop for this message.

        kafkaTemplate.send(kafkaTopic, invalidKafkaPayload);

        // Verify handleMessage itself was called (Spring Kafka calls it)
        verify(kafkaMessageConsumerHandler, timeout(5000).atLeastOnce()).handleMessage(any());

        // Crucially, verify that due to processing error, dispatch and save were not reached.
        verify(eventMessageRepositoryMock, after(1000).never()).save(any(EventMessage.class));
        verify(domainEventDispatcherMock, after(1000).never()).dispatch(any(EventEnvelope.class));
        // Log verification would be ideal here if a test appender was set up.
    }
}
