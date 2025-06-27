package com.ewallet.dom.controller;

import com.ewallet.dom.dto.*;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.mapper.TransactionMappingService;
import com.ewallet.dom.service.UserService;
import com.ewallet.dom.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserService userService;

    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance(@AuthenticationPrincipal UserDetails currentUser) {
        User principalUser = userService.getUserByName(currentUser.getUsername());
        Wallet wallet = walletService.findWalletByUserID(principalUser.getId());
        return ResponseEntity.ok(new WalletResponse(wallet.getBalance(),"User's current balance."));
    }

    @PostMapping("/deposit")
    public CompletableFuture<Wallet> deposit(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody DepositRequest request) {
        User principalUser = userService.getUserByName(currentUser.getUsername());
        return walletService.processTransaction(TransactionMappingService.fromDepositRequest(principalUser,request), true);
    }

    @PostMapping("/withdraw")
    public CompletableFuture<Wallet> withdraw(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody WithdrawRequest request) {
        User principalUser = userService.getUserByName(currentUser.getUsername());
            return walletService.processTransaction(TransactionMappingService.fromWithdrawRequest(principalUser,request), true);
    }

    @PostMapping("/transfer")
    public CompletableFuture<Wallet> transfer(@AuthenticationPrincipal UserDetails currentUser, @Valid @RequestBody TransferRequest request) {
        User principalUser = userService.getUserByName(currentUser.getUsername());
            return walletService.processTransaction(TransactionMappingService.fromTransferRequest(principalUser,request), true);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@AuthenticationPrincipal UserDetails currentUser) {
        User principalUser = userService.getUserByName(currentUser.getUsername());
        Wallet wallet = walletService.findWalletByUserID(principalUser.getId());
        List<Transaction> transactions = walletService.getTransactionsForWallet(wallet.getId());
        return ResponseEntity.ok(transactions);
    }
}