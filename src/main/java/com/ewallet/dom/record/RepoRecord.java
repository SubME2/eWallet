package com.ewallet.dom.record;

import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;

public record RepoRecord(UserRepository userRepository, WalletRepository walletRepository, TransactionRepository transactionRepository, IdempotencyKeyRepository idempotencyKeyRepository) {
}
