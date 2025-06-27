package com.ewallet.dom.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@EqualsAndHashCode
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

//    @JsonBackReference
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "wallet_id", nullable = false)
//    private Wallet wallet; // The wallet associated with this transaction (could be sender's or receiver's view)

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private String senderUsername; // Store username for easier readability

    @Column(nullable = false)
    private String receiverUsername; // Store username for easier readability

    @Column(nullable = false)
    private double amount;

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

    public Transaction(Wallet wallet, String senderUsername, String receiverUsername, double amount, TransactionType type) {
        this.walletId = wallet.getId();
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.amount = amount;
        this.type = type;
    }
}
