package org.mifos.loanrisk.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.domain.Aggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@DataR2dbcTest
class AggregatorRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private AggregatorRepository aggregatorRepository;

    @BeforeEach
    void cleanup() {
        // For R2DBC tests, data is often rolled back.
        // If explicit cleanup is needed (e.g. due to test setup not using transactions fully):
        // aggregatorRepository.deleteAll().block();
    }

    private Aggregator createTestAggregator(Long loanId, BigDecimal score) {
        Aggregator agg = new Aggregator();
        agg.setLoanId(loanId);
        agg.setOverallScore(score);
        agg.setBankStmtUploaded(true);
        agg.setIdDocUploaded(false);
        agg.setKycDocUploaded(true);
        agg.setAssessmentStatus("PENDING_REVIEW");
        agg.setLastUpdated(LocalDateTime.now());
        // Set other fields as necessary
        return agg;
    }

    @Test
    void findByLoanId_whenAggregatorExists_shouldReturnAggregator() {
        Long loanId = 301L;
        Aggregator agg = createTestAggregator(loanId, BigDecimal.valueOf(85.25));

        Mono<Aggregator> setup = aggregatorRepository.save(agg);
        Mono<Aggregator> find = aggregatorRepository.findByLoanId(loanId);

        StepVerifier.create(setup.then(find))
                .assertNext(foundAggregator -> {
                    assertNotNull(foundAggregator.getId());
                    assertEquals(loanId, foundAggregator.getLoanId());
                    assertTrue(BigDecimal.valueOf(85.25).compareTo(foundAggregator.getOverallScore()) == 0);
                    assertTrue(foundAggregator.isBankStmtUploaded());
                    assertEquals("PENDING_REVIEW", foundAggregator.getAssessmentStatus());
                })
                .verifyComplete();
    }

    @Test
    void findByLoanId_whenAggregatorDoesNotExist_shouldReturnEmpty() {
        StepVerifier.create(aggregatorRepository.findByLoanId(9990L))
                .verifyComplete();
    }

    @Test
    void save_shouldPersistNewAggregatorAndAssignId() {
        Long loanId = 302L;
        Aggregator agg = createTestAggregator(loanId, BigDecimal.valueOf(70.00));

        StepVerifier.create(aggregatorRepository.save(agg))
                .assertNext(savedAggregator -> {
                    assertNotNull(savedAggregator.getId());
                    assertEquals(loanId, savedAggregator.getLoanId());
                    assertTrue(BigDecimal.valueOf(70.00).compareTo(savedAggregator.getOverallScore()) == 0);
                })
                .verifyComplete();

        // Verify it can be fetched
        StepVerifier.create(aggregatorRepository.findByLoanId(loanId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void save_updateExistingAggregator() {
        Long loanId = 303L;
        Aggregator initialAgg = createTestAggregator(loanId, BigDecimal.valueOf(60.50));
        initialAgg.setAssessmentStatus("INITIAL_ASSESSMENT");

        Aggregator savedInitial = aggregatorRepository.save(initialAgg).block();
        assertNotNull(savedInitial);
        assertNotNull(savedInitial.getId());

        // Update
        savedInitial.setOverallScore(BigDecimal.valueOf(65.75));
        savedInitial.setAssessmentStatus("RE_ASSESSED");
        savedInitial.setIdDocUploaded(true);

        StepVerifier.create(aggregatorRepository.save(savedInitial))
                .assertNext(updatedAggregator -> {
                    assertEquals(savedInitial.getId(), updatedAggregator.getId());
                    assertEquals(loanId, updatedAggregator.getLoanId());
                    assertTrue(BigDecimal.valueOf(65.75).compareTo(updatedAggregator.getOverallScore()) == 0);
                    assertEquals("RE_ASSESSED", updatedAggregator.getAssessmentStatus());
                    assertTrue(updatedAggregator.isIdDocUploaded());
                })
                .verifyComplete();

        StepVerifier.create(aggregatorRepository.findById(savedInitial.getId()))
            .assertNext(fetchedAggregator -> {
                 assertTrue(BigDecimal.valueOf(65.75).compareTo(fetchedAggregator.getOverallScore()) == 0);
                 assertEquals("RE_ASSESSED", fetchedAggregator.getAssessmentStatus());
            })
            .verifyComplete();
    }
}
