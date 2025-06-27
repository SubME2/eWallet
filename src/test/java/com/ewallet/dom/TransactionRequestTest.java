package com.ewallet.dom;

import com.ewallet.dom.constant.TransactionRequestType;
import com.ewallet.dom.record.TransactionRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionRequestTest {

    @Test
    void shouldThrowNullPointerExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () -> new TransactionRequest(
                null, "receiver", 100.0, "idempotencyKey", TransactionRequestType.TRANSFER, 0));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverUsernameIsNullForTransfer() {
        assertThrows(NullPointerException.class, () -> new TransactionRequest(
                1L, null, 100.0, "idempotencyKey", TransactionRequestType.TRANSFER, 0));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenAmountIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionRequest(
                1L, "receiver", -100.0, "idempotencyKey", TransactionRequestType.TRANSFER, 0));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenIdempotencyKeyIsNull() {
        assertThrows(NullPointerException.class, () -> new TransactionRequest(
                1L, "receiver", 100.0, null, TransactionRequestType.TRANSFER, 0));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransactionRequestTypeIsNull() {
        assertThrows(NullPointerException.class, () -> new TransactionRequest(
                1L, "receiver", 100.0, "idempotencyKey", null, 0));
    }

    @Test
    void shouldNotThrowExceptionForValidTransferRequest() {
        assertDoesNotThrow(() -> new TransactionRequest(
                1L, "receiver", 100.0, "idempotencyKey", TransactionRequestType.TRANSFER, 0));
    }
}

