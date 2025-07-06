package com.ewallet.dom.controller;

import com.ewallet.dom.dto.*;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.mapper.TransactionMappingService;
import com.ewallet.dom.service.TransactionService;
import com.ewallet.dom.service.UserService;
import com.ewallet.dom.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance(@AuthenticationPrincipal UserDetails currentUser) {
        Wallet wallet = walletService.findWalletByUserID(currentUser.getUsername());
        return ResponseEntity.ok(new WalletResponse(wallet.getBalance(),"User's current balance."));
    }

    @PostMapping("/deposit")
    public CompletableFuture<Wallet> deposit(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody DepositRequest request) {
        return walletService.processTransaction(TransactionMappingService.fromDepositRequest(currentUser.getUsername(),request), true);
    }

    @PostMapping("/withdraw")
    public CompletableFuture<Wallet> withdraw(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody WithdrawRequest request) {
            return walletService.processTransaction(TransactionMappingService.fromWithdrawRequest(currentUser.getUsername(),request), true);
    }

    @PostMapping("/transfer")
    public CompletableFuture<Wallet> transfer(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody TransferRequest request) {
            return walletService.processTransaction(TransactionMappingService.fromTransferRequest(currentUser.getUsername(),request), true);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@AuthenticationPrincipal UserDetails currentUser) {
        Wallet wallet = walletService.findWalletByUserID(currentUser.getUsername());
        List<Transaction> transactions = walletService.getTransactionsForWallet(wallet.getId());
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/transactions/range")
    public ResponseEntity<List<Transaction>> getTransactionsForCurrentUserByDateRange(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<Transaction> transactions = transactionService.getTransactionsForWalletByDateRange(
                currentUser.getUsername(), startDate, endDate
        );
        return ResponseEntity.ok(transactions);
    }

}