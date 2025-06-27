package com.ewallet.dom.repository;

import com.ewallet.dom.exception.InsufficientFundsException;
import com.ewallet.dom.model.Wallet;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    @Transactional
    @Lock(LockModeType.READ)
    Optional<Wallet> findByUserId(Long userId);

    @Transactional
    @Modifying
    @Query("update wallets set balance =( balance + :amount), updatedAt = cast(now() as timestamp) where id = :id ")
    int addAmount(@Param(value = "amount") double amount, @Param(value = "id") UUID id);


    @Transactional
    @Modifying
    @Query("update wallets set balance = ( balance - :amount ) , updatedAt = cast(now() as timestamp) where id = :id and balance >= :amount")
    int subAmountFromBalance(@Param(value = "amount") double amount, @Param(value = "id") UUID id);

    default void subAmount(double amount,@NotNull UUID id){
        int succuss = subAmountFromBalance(amount,id);
        if (succuss <= 0 ) throw new InsufficientFundsException("User with id: " + id + " has insufficient balance");
    }
}
