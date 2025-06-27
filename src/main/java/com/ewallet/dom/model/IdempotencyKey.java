package com.ewallet.dom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String key; // The UUID provided by the client


    @Column( nullable = false)
    private boolean completed = false; // set false for new key

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String operationType; // e.g., "DEPOSIT", "WITHDRAWAL", "TRANSFER"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // Link to the user who made the request
    private User user;

    public IdempotencyKey(String key, String operationType, User user) {
        this.key = key;
        this.operationType = operationType;
        this.user = user;
    }
}
