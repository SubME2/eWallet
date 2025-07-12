package com.ewallet.dom.service;

import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {


    private final WalletService walletService;
    private final UserService userService;
    private final TransactionRepository transactionRepository;


    /**
     * Retrieves ledger entries for a wallet within a specified date range.
     *
     * @param userName The Name of the signedIn user.
     * @param startDate The start date (inclusive).
     * @param endDate The end date (inclusive).
     * @return A list of ledger entries within the date range.
     */
    public List<Transaction> getTransactionsForWalletByDateRange(String userName, LocalDate startDate, LocalDate endDate) {
        Wallet wallet = walletService.findWalletByUserID(userName);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        return transactionRepository.findByWalletIdAndTimestampBetweenOrderByTimestampAsc(
                wallet.getId(), startDateTime, endDateTime
        );
    }
}
