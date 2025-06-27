package com.ewallet.dom.repository;

import com.ewallet.dom.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWalletIdOrderByTimestampDesc(UUID walletId);

    // New method for date range queries
    List<Transaction> findByWalletIdAndTimestampBetweenOrderByTimestampAsc(
            UUID walletId, LocalDateTime startDateTime, LocalDateTime endDateTime);
}
