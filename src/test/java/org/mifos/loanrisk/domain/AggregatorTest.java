package org.mifos.loanrisk.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mifos.loanrisk.document.common.DocumentType;
import static org.junit.jupiter.api.Assertions.*;

class AggregatorTest {

    private Aggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new Aggregator();
    }

    @Test
    void documentArrived_shouldSetCorrectFlagToTrue() {
        // Test for BANK_STATEMENT
        aggregator.documentArrived(DocumentType.BANK_STATEMENT);
        assertTrue(aggregator.isBankStmtUploaded());
        assertFalse(aggregator.isIdDocUploaded());
        assertFalse(aggregator.isKycDocUploaded());

        setUp(); // Reset aggregator
        aggregator.documentArrived(DocumentType.ID_DOCUMENT);
        assertTrue(aggregator.isIdDocUploaded());
        assertFalse(aggregator.isBankStmtUploaded());
        assertFalse(aggregator.isKycDocUploaded());

        setUp(); // Reset aggregator
        aggregator.documentArrived(DocumentType.KYC_DOCUMENT);
        assertTrue(aggregator.isKycDocUploaded());
        assertFalse(aggregator.isBankStmtUploaded());
        assertFalse(aggregator.isIdDocUploaded());
    }

    @Test
    void documentArrived_shouldNotChangeOtherFlags() {
        aggregator.setKycDocUploaded(true); // Pre-set a flag

        aggregator.documentArrived(DocumentType.BANK_STATEMENT);
        assertTrue(aggregator.isBankStmtUploaded());
        assertTrue(aggregator.isKycDocUploaded()); // Ensure pre-set flag is still true
        assertFalse(aggregator.isIdDocUploaded());
    }

    @Test
    void documentDeleted_shouldSetCorrectFlagToFalse() {
        // First, set all to true
        aggregator.setBankStmtUploaded(true);
        aggregator.setIdDocUploaded(true);
        aggregator.setKycDocUploaded(true);

        // Test for BANK_STATEMENT
        aggregator.documentDeleted(DocumentType.BANK_STATEMENT);
        assertFalse(aggregator.isBankStmtUploaded());
        assertTrue(aggregator.isIdDocUploaded());
        assertTrue(aggregator.isKycDocUploaded());

        // Reset and test for ID_DOCUMENT
        aggregator.setBankStmtUploaded(true); // Reset deleted flag
        aggregator.documentDeleted(DocumentType.ID_DOCUMENT);
        assertFalse(aggregator.isIdDocUploaded());
        assertTrue(aggregator.isBankStmtUploaded());
        assertTrue(aggregator.isKycDocUploaded());

        // Reset and test for KYC_DOCUMENT
        aggregator.setIdDocUploaded(true); // Reset deleted flag
        aggregator.documentDeleted(DocumentType.KYC_DOCUMENT);
        assertFalse(aggregator.isKycDocUploaded());
        assertTrue(aggregator.isBankStmtUploaded());
        assertTrue(aggregator.isIdDocUploaded());
    }

    @Test
    void documentDeleted_whenFlagAlreadyFalse_shouldRemainFalse() {
        assertFalse(aggregator.isBankStmtUploaded()); // Ensure it's initially false
        aggregator.documentDeleted(DocumentType.BANK_STATEMENT);
        assertFalse(aggregator.isBankStmtUploaded());
    }

    // Test for other potential logic in Aggregator, e.g., score calculations, status updates
    // if such logic is added to the Aggregator domain object itself.
    // For example:
    // @Test
    // void testDefaultAssessmentStatus() {
    //     assertEquals("PENDING_ASSESSMENT", aggregator.getAssessmentStatus()); // Or whatever default is
    // }
}
