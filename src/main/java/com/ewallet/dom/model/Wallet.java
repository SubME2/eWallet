package com.ewallet.dom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "wallets")
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id = null;

    @Column(name = "user_id",nullable = false)
    private Long userId;

    @Column(nullable = false)
    private double balance;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version // <--- Add this annotation
    private Long version; // <--- This field will manage optimistic locking

    // Custom constructor for initial creation
    public Wallet(User user, double initialBalance) {
        this.userId = user.getId();
        this.balance = initialBalance;
    }


}