package com.ewallet.dom.transferservice;

import com.ewallet.dom.BaseIntegrationTest;
import com.ewallet.dom.dto.TransferRequest;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.ewallet.dom.executable.TransferFund;
import com.ewallet.dom.mapper.TransactionMappingService;
import com.ewallet.dom.service.WalletService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
//(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class FundTransferServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory emf;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    //private TransferFund service;

    private User alice, bob;
    private Wallet aliceWallet, bobWallet;

    @BeforeEach
    void setup() {
        walletRepository.deleteAll();
        transactionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        userRepository.deleteAll();
        //service = new TransferFund( emf);
        alice = new User();
        alice.setUsername("alice");
        alice.setPassword("p");
        alice = userRepository.save(alice);
        bob = new User();
        bob.setUsername("bob");
        bob.setPassword("p");
        bob = userRepository.save(bob);


    }


    @Test
    void concurrentTransfers_withIdempotencyHandleCorrectly() throws InterruptedException {
        long keyId = 100L;
        int threadCount = 5;
        double transferAmount = 30;
        double initialBalanceAlice = 1000;
        double initialBalanceBob = 25;

        aliceWallet = new Wallet();
        aliceWallet.setUserId(alice.getId());
        aliceWallet.setBalance(initialBalanceAlice);
        aliceWallet = walletRepository.save(aliceWallet);

        bobWallet = new Wallet();
        bobWallet.setUserId(bob.getId());
        bobWallet.setBalance(initialBalanceBob);
        bobWallet = walletRepository.save(bobWallet);

        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        String idempotencyKey = UUID.randomUUID().toString();
        for (int i = 0; i < threadCount; i++) {
            exec.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    // All threads attempt the same idempotent transfer
                    RepoRecord repoRecord = new RepoRecord(userRepository,walletRepository,transactionRepository,idempotencyKeyRepository);
                    TransferFund transferFund = new TransferFund(repoRecord,getTransactionRequest(transferAmount,idempotencyKey));
                    transferFund.run();
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for both threads to be ready
        readyLatch.await();
        // Let them go at same time
        startLatch.countDown();
        doneLatch.await();
        exec.shutdown();

        // Expect only one execution applied
        Wallet al = walletRepository.findByUserId(alice.getId()).orElseThrow();
        Wallet bo = walletRepository.findByUserId(bob.getId()).orElseThrow();

        assertThat(al.getBalance()).isEqualTo(initialBalanceAlice - transferAmount );
        assertThat(bo.getBalance()).isEqualTo(55);

        // Only 2 transactions created
        List<Transaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(2);
    }

    @Test
    void concurrentTransfers_withIdempotencyHandleCorrectlyWithRetry() throws InterruptedException {
        long keyId = 100L;
        int threadCount = 15;
        double transferAmount = 30;
        double initialBalanceAlice = 1000;
        double initialBalanceBob = 25;

        aliceWallet = new Wallet();
        aliceWallet.setUserId(alice.getId());
        aliceWallet.setBalance(initialBalanceAlice);
        aliceWallet = walletRepository.save(aliceWallet);

        bobWallet = new Wallet();
        bobWallet.setUserId(bob.getId());
        bobWallet.setBalance(initialBalanceBob);
        bobWallet = walletRepository.save(bobWallet);

        List<Wallet> wallets = new ArrayList<>();

        ExecutorService exec = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            exec.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    // All threads attempt the same idempotent transfer
                    CompletableFuture<Wallet> completableFuture =  walletService.processTransaction(getNewTransactionRequest(transferAmount), true);
                    wallets.add(completableFuture.get());
                } catch (InterruptedException | ExecutionException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for both threads to be ready
        readyLatch.await();
        // Let them go at same time
        startLatch.countDown();
        doneLatch.await();
        exec.shutdown();

        // Expect only one execution applied
        Wallet al = walletRepository.findByUserId(alice.getId()).orElseThrow();
        Wallet bo = walletRepository.findByUserId(bob.getId()).orElseThrow();

        assertThat(al.getBalance()).isEqualTo(initialBalanceAlice - ( threadCount * transferAmount));
        assertThat(bo.getBalance()).isEqualTo(initialBalanceBob + (threadCount * transferAmount));

        // Only 2 transactions created
        List<Transaction> txs = transactionRepository.findAll();
        assertThat(txs).hasSize(2 * threadCount);
    }

    private @NotNull TransactionRequest getNewTransactionRequest(double transferAmount) {
        return TransactionMappingService
                .fromTransferRequest(alice,
                        getTransferRequest(bob.getUsername(), transferAmount, UUID.randomUUID().toString()));
    }

    private @NotNull TransactionRequest getTransactionRequest(double transferAmount, String idempotencyKey) {
        return TransactionMappingService
                .fromTransferRequest(alice,
                        getTransferRequest(bob.getUsername(), transferAmount, idempotencyKey));
    }

    private @NotNull TransferRequest getTransferRequest(String receiverUser, double transferAmount, String idempotencyKey) {
        return new TransferRequest(receiverUser, transferAmount, idempotencyKey);
    }


}
