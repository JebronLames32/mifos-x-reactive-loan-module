package org.mifos.loanrisk.document.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "bankStatement, BANK_STATEMENT",
            "BANKSTATEMENT, BANK_STATEMENT", // Case-insensitivity
            "idDocument, ID_DOC",
            "IDDOCUMENT, ID_DOC",         // Case-insensitivity
            "kycDocument, KYC_DOC",
            "KYCDOCUMENT, KYC_DOC"          // Case-insensitivity
    })
    void of_shouldMapKnownWireNames(String wireName, String expectedDocumentTypeName) {
        DocumentType expectedDocumentType = DocumentType.valueOf(expectedDocumentTypeName);
        assertThat(DocumentType.of(wireName)).isEqualTo(expectedDocumentType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknownDoc", "bs", ""})
    void of_shouldThrowException_forUnknownWireName(String unknownWireName) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            DocumentType.of(unknownWireName);
        });
        assertThat(exception.getMessage()).isEqualTo("Unknown document name: " + unknownWireName);
    }

    @Test
    void of_shouldThrowException_forNullInput() {
        // Current implementation of(String raw) does not explicitly check for null
        // and will likely result in a NullPointerException from raw.equalsIgnoreCase().
        // Depending on desired behavior, an explicit null check might be added to .of().
        // For now, testing existing behavior.
        assertThrows(NullPointerException.class, () -> {
            DocumentType.of(null);
        });
    }
}
