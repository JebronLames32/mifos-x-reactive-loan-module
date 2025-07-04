package org.mifos.loanrisk.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.config.AbstractIntegrationTest;
import org.mifos.loanrisk.domain.LoanSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoanSnapshotRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private LoanSnapshotRepository loanSnapshotRepository;

    @BeforeEach
    void cleanup() {
        loanSnapshotRepository.deleteAll().block();
    }

    private LoanSnapshot createTestLoanSnapshot(Long loanId, String payloadContent) {
        return new LoanSnapshot(loanId, "{\"data\":\"" + payloadContent + "\"}", LocalDateTime.now());
    }

    @Test
    void saveAndFindById_shouldWork() {
        LoanSnapshot snapshot = createTestLoanSnapshot(1L, "payload1");

        StepVerifier.create(loanSnapshotRepository.save(snapshot))
                .assertNext(savedSnapshot -> {
                    assertThat(savedSnapshot.getId()).isNotNull();
                    assertThat(savedSnapshot.getLoanId()).isEqualTo(1L);
                    assertThat(savedSnapshot.getPayload()).contains("payload1");

                    StepVerifier.create(loanSnapshotRepository.findById(savedSnapshot.getId()))
                            .assertNext(foundSnapshot -> {
                                assertThat(foundSnapshot.getLoanId()).isEqualTo(1L);
                                assertThat(foundSnapshot.getPayload()).isEqualTo(savedSnapshot.getPayload());
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }

    @Test
    void findByLoanId_shouldReturnCorrectSnapshot() {
        LoanSnapshot snapshot1 = createTestLoanSnapshot(10L, "snap10");
        LoanSnapshot snapshot2 = createTestLoanSnapshot(11L, "snap11");
        loanSnapshotRepository.save(snapshot1).block();
        loanSnapshotRepository.save(snapshot2).block();

        StepVerifier.create(loanSnapshotRepository.findByLoanId(10L))
                .assertNext(foundSnapshot -> {
                    assertThat(foundSnapshot.getLoanId()).isEqualTo(10L);
                    assertThat(foundSnapshot.getPayload()).contains("snap10");
                })
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.findByLoanId(11L))
                .assertNext(foundSnapshot -> {
                    assertThat(foundSnapshot.getLoanId()).isEqualTo(11L);
                    assertThat(foundSnapshot.getPayload()).contains("snap11");
                })
                .verifyComplete();
    }

    @Test
    void findByLoanId_shouldReturnEmpty_whenNotFound() {
        StepVerifier.create(loanSnapshotRepository.findByLoanId(999L))
                .verifyComplete();
    }

    @Test
    void existsByLoanId_shouldReturnTrue_whenExists() {
        LoanSnapshot snapshot = createTestLoanSnapshot(20L, "snap20");
        loanSnapshotRepository.save(snapshot).block();

        StepVerifier.create(loanSnapshotRepository.existsByLoanId(20L))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void existsByLoanId_shouldReturnFalse_whenNotExists() {
        StepVerifier.create(loanSnapshotRepository.existsByLoanId(998L))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void deleteByLoanId_shouldDeleteSnapshot() {
        LoanSnapshot snapshot = createTestLoanSnapshot(30L, "snap30");
        loanSnapshotRepository.save(snapshot).block();

        StepVerifier.create(loanSnapshotRepository.existsByLoanId(30L))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.deleteByLoanId(30L))
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.existsByLoanId(30L))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void deleteAll_shouldRemoveAllSnapshots() {
        LoanSnapshot snapshot1 = createTestLoanSnapshot(40L, "s40");
        LoanSnapshot snapshot2 = createTestLoanSnapshot(41L, "s41");
        loanSnapshotRepository.save(snapshot1).block();
        loanSnapshotRepository.save(snapshot2).block();

        StepVerifier.create(loanSnapshotRepository.count())
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.deleteAll())
                .verifyComplete();

        StepVerifier.create(loanSnapshotRepository.count())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void save_shouldUpdateExistingSnapshot_whenSameId() {
        LoanSnapshot originalSnapshot = createTestLoanSnapshot(50L, "original_payload");
        LoanSnapshot savedOriginal = loanSnapshotRepository.save(originalSnapshot).block();
        assertThat(savedOriginal).isNotNull();
        Long generatedId = savedOriginal.getId();

        LoanSnapshot snapshotToUpdate = new LoanSnapshot(50L, "updated_payload", LocalDateTime.now().plusHours(1));
        snapshotToUpdate.setId(generatedId); // Set the same ID to trigger an update

        StepVerifier.create(loanSnapshotRepository.save(snapshotToUpdate))
                .assertNext(updatedSnapshot -> {
                    assertThat(updatedSnapshot.getId()).isEqualTo(generatedId);
                    assertThat(updatedSnapshot.getLoanId()).isEqualTo(50L); // Should remain the same
                    assertThat(updatedSnapshot.getPayload()).contains("updated_payload");
                    assertThat(updatedSnapshot.getSnapshotAt()).isAfter(savedOriginal.getSnapshotAt());
                })
                .verifyComplete();

        // Verify count is still 1
        StepVerifier.create(loanSnapshotRepository.count())
                .expectNext(1L)
                .verifyComplete();
    }
}
