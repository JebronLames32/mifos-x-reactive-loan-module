package org.mifos.loanrisk.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanStatusTest {

    @ParameterizedTest
    @CsvSource({
            "100, SUBMITTED_AND_PENDING_APPROVAL",
            "200, APPROVED",
            "300, ACTIVE",
            "303, TRANSFER_IN_PROGRESS",
            "304, TRANSFER_ON_HOLD",
            "400, WITHDRAWN_BY_CLIENT",
            "500, REJECTED",
            "600, CLOSED_OBLIGATIONS_MET",
            "601, CLOSED_WRITTEN_OFF",
            "602, CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT",
            "700, OVERPAID"
    })
    void fromInt_shouldMapKnownFineractStatusCodes(int fineractStatusId, String expectedLoanStatusName) {
        LoanStatus expectedLoanStatus = LoanStatus.valueOf(expectedLoanStatusName);
        assertThat(LoanStatus.fromInt(fineractStatusId)).isEqualTo(expectedLoanStatus);
    }

    @Test
    void fromInt_shouldReturnInvalid_forUnknownStatusCode() {
        int unknownStatusCode = 9999;
        assertThat(LoanStatus.fromInt(unknownStatusCode)).isEqualTo(LoanStatus.INVALID);
    }

    @Test
    void fromInt_shouldReturnInvalid_forNullInput() {
        assertThat(LoanStatus.fromInt(null)).isEqualTo(LoanStatus.INVALID);
    }

    @Test
    void getValue_shouldReturnCorrectFineractId() {
        assertThat(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getValue()).isEqualTo(100);
        assertThat(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL.getCode()).isEqualTo("loanStatusType.submitted.and.pending.approval");
    }
}
