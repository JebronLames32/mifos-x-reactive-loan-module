package org.mifos.loanrisk.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.common.LoanStatus;
import org.mifos.loanrisk.domain.LoanSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DataR2dbcTest
class LoanSnapshotRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private LoanSnapshotRepository loanSnapshotRepository;

    @BeforeEach
    void cleanup() {
        // loanSnapshotRepository.deleteAll().block(); // If explicit cleanup is needed
    }

    private LoanSnapshot createTestSnapshot(Long loanId, String accountNo, BigDecimal principal, String status) {
        LoanSnapshot snapshot = new LoanSnapshot();
        snapshot.setLoanId(loanId);
        snapshot.setAccountNo(accountNo);
        snapshot.setPrincipal(principal);
        snapshot.setAnnualNominalInterestRate(BigDecimal.valueOf(12.5));
        snapshot.setSubmittedOnDate(LocalDate.now().minusDays(10));
        snapshot.setExpectedDisbursementDate(LocalDate.now().plusDays(1));
        snapshot.setStatus(status);
        snapshot.setAvroPayload(ByteBuffer.wrap(("Test Avro for " + accountNo).getBytes()));
        snapshot.setLastUpdated(LocalDateTime.now());
        // Set other fields as necessary
        return snapshot;
    }

    @Test
    void findByLoanId_whenSnapshotExists_shouldReturnSnapshot() {
        Long loanId = 401L;
        LoanSnapshot snapshot = createTestSnapshot(loanId, "LS-401", BigDecimal.valueOf(20000), LoanStatus.PENDING_APPROVAL.name());

        Mono<LoanSnapshot> setup = loanSnapshotRepository.save(snapshot);
        Mono<LoanSnapshot> find = loanSnapshotRepository.findByLoanId(loanId);

        StepVerifier.create(setup.then(find))
                .assertNext(foundSnapshot -> {
                    assertNotNull(foundSnapshot.getId());
                    assertEquals(loanId, foundSnapshot.getLoanId());
                    assertEquals("LS-401", foundSnapshot.getAccountNo());
                    assertTrue(BigDecimal.valueOf(20000).compareTo(foundSnapshot.getPrincipal()) == 0);
                    assertEquals(LoanStatus.PENDING_APPROVAL.name(), foundSnapshot.getStatus());
                    assertArrayEquals(("Test Avro for LS-401").getBytes(), foundSnapshot.getAvroPayload().array());
                })
                .verifyComplete();
    }

    @Test
    void findByLoanId_whenSnapshotDoesNotExist_shouldReturnEmpty() {
        StepVerifier.create(loanSnapshotRepository.findByLoanId(9991L))
                .verifyComplete();
    }

    @Test
    void save_shouldPersistNewSnapshotAndAssignId() {
        Long loanId = 402L;
        LoanSnapshot snapshot = createTestSnapshot(loanId, "LS-402", BigDecimal.valueOf(25000), LoanStatus.APPROVED.name());

        StepVerifier.create(loanSnapshotRepository.save(snapshot))
                .assertNext(savedSnapshot -> {
                    assertNotNull(savedSnapshot.getId());
                    assertEquals(loanId, savedSnapshot.getLoanId());
                    assertTrue(BigDecimal.valueOf(25000).compareTo(savedSnapshot.getPrincipal()) == 0);
                    assertEquals(LoanStatus.APPROVED.name(), savedSnapshot.getStatus());
                })
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.findByLoanId(loanId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void save_updateExistingSnapshot() {
        Long loanId = 403L;
        LoanSnapshot initialSnapshot = createTestSnapshot(loanId, "LS-403", BigDecimal.valueOf(30000), LoanStatus.APPROVED.name());

        LoanSnapshot savedInitial = loanSnapshotRepository.save(initialSnapshot).block();
        assertNotNull(savedInitial);
        assertNotNull(savedInitial.getId());

        // Update
        savedInitial.setPrincipal(BigDecimal.valueOf(32000));
        savedInitial.setStatus(LoanStatus.DISBURSED.name()); // Assuming DISBURSED is a status
        savedInitial.setAvroPayload(ByteBuffer.wrap("Updated Avro for LS-403".getBytes()));

        StepVerifier.create(loanSnapshotRepository.save(savedInitial))
                .assertNext(updatedSnapshot -> {
                    assertEquals(savedInitial.getId(), updatedSnapshot.getId());
                    assertEquals(loanId, updatedSnapshot.getLoanId());
                    assertTrue(BigDecimal.valueOf(32000).compareTo(updatedSnapshot.getPrincipal()) == 0);
                    assertEquals(LoanStatus.DISBURSED.name(), updatedSnapshot.getStatus());
                    assertArrayEquals("Updated Avro for LS-403".getBytes(), updatedSnapshot.getAvroPayload().array());
                })
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.findById(savedInitial.getId()))
            .assertNext(fetchedSnapshot -> {
                 assertTrue(BigDecimal.valueOf(32000).compareTo(fetchedSnapshot.getPrincipal()) == 0);
                 assertEquals(LoanStatus.DISBURSED.name(), fetchedSnapshot.getStatus());
            })
            .verifyComplete();
    }
}
