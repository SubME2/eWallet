package com.ewallet.dom.model;


import com.ewallet.dom.record.TransactionDetailRecord;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "transactions")
@Table(name = "transactions")
@Data
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private String senderUsername; // Store username for easier readability

    @Column(nullable = false)
    private String receiverUsername; // Store username for easier readability

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private double preBalance;

    @Column(nullable = false)
    private double postBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // In a real system, you'd add a digital signature field here
    // private String signature;

    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_SENT,
        TRANSFER_RECEIVED
    }

    public Transaction(TransactionDetailRecord transactionDetailRecord) {
        this.walletId = transactionDetailRecord.walletId();
        this.senderUsername = transactionDetailRecord.senderUserName();
        this.receiverUsername = transactionDetailRecord.receiverUserName();
        this.amount = transactionDetailRecord.amount();
        this.type = transactionDetailRecord.type();
    }
}
