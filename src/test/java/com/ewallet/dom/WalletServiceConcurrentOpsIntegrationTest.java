package com.ewallet.dom;

import com.ewallet.dom.constant.TransactionRequestType;
import com.ewallet.dom.dto.DepositRequest;
import com.ewallet.dom.dto.RegisterRequest;
import com.ewallet.dom.dto.TransferRequest;
import com.ewallet.dom.dto.WithdrawRequest;
import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.exception.InsufficientFundsException;
import com.ewallet.dom.executable.WithdrawFundX;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.ewallet.dom.service.AuthService;
import com.ewallet.dom.mapper.TransactionMappingService;
import com.ewallet.dom.service.WalletService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class WalletServiceConcurrentOpsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private AuthService authService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private User testUser;
    private User receiverUser;

    @BeforeEach
    void setup() {

        walletRepository.deleteAll();
        transactionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        userRepository.deleteAll();

//        RetryContext retryContext = Mockito.mock(RetryContext.class);
//        RetrySynchronizationManager.register(retryContext);
        // Register initial users for tests
        RegisterRequest registerRequest1 = new RegisterRequest();
        registerRequest1.setUsername("testuser_wallet");
        registerRequest1.setPassword("pass123");
        testUser = authService.register(registerRequest1);

        RegisterRequest registerRequest2 = new RegisterRequest();
        registerRequest2.setUsername("receiver_user");
        registerRequest2.setPassword("pass123");
        receiverUser = authService.register(registerRequest2);
    }

    // --- Existing test methods (omitted for brevity, assume they are still here) ---

    @Test
    @DisplayName("Should handle concurrent deposits correctly with optimistic locking")
    void shouldHandleConcurrentDepositsCorrectlyInSync() throws InterruptedException, ExecutionException {
        int initialBalance = 100;
        int numDeposits = 20;
        double depositAmount = 10.0;
        TransactionRequest transactionRequest = new TransactionRequest(testUser.getUsername(),null,initialBalance,UUID.randomUUID().toString(),TransactionRequestType.DEPOSIT, 0);

        // Ensure user has initial balance for the test
        CompletableFuture<Wallet> walletCompletableFuture = walletService.processTransaction(transactionRequest, true);
        walletCompletableFuture.get();
        assertEquals(initialBalance, walletRepository.findByUserId(testUser.getId()).get().getBalance());

        AtomicInteger successfulDeposits = new AtomicInteger(0);

        final List<CompletableFuture<Void>> all = new ArrayList<>();

        for (int i = 0; i < numDeposits; i++) {
            TransactionRequest transactionRequest1 = new TransactionRequest(testUser.getUsername(),null,depositAmount,UUID.randomUUID().toString(),TransactionRequestType.DEPOSIT, 0);
            walletCompletableFuture = walletService.processTransaction(transactionRequest1, true);
            walletCompletableFuture.get();
            successfulDeposits.incrementAndGet();
        }

        //walletCompletableFuture.get();

        // Verify final balance
        Wallet finalWallet = walletRepository.findByUserId(testUser.getId()).get();
        assertEquals(initialBalance + (numDeposits * depositAmount), finalWallet.getBalance(), 0.001);

        // Verify transaction count
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalWallet.getId());
        // Initial deposit + numDeposits concurrent deposits
        assertThat(transactions).hasSize(1 + numDeposits);
        assertEquals(numDeposits, successfulDeposits.get());
    }

    @Test
    @DisplayName("Should handle concurrent deposits correctly with optimistic locking")
    void shouldHandleConcurrentDepositsCorrectly() throws InterruptedException, ExecutionException {
        int initialBalance = 100;
        int numDeposits = 10; // limited by property  ## spring.datasource.hikari.maximum-pool-size=10
        double depositAmount = 10.0;
        RegisterRequest registerRequest1 = new RegisterRequest();
        registerRequest1.setUsername("testuser_wallet4");
        registerRequest1.setPassword("pass1234");
        User testUserX = authService.register(registerRequest1);
        TransactionRequest transactionRequest = new TransactionRequest(testUserX.getUsername(),null,initialBalance,UUID.randomUUID().toString(),TransactionRequestType.DEPOSIT, 0);
        // Ensure user has initial balance for the test
        CompletableFuture<Wallet> walletCompletableFuture = walletService.processTransaction(transactionRequest, true);
        walletCompletableFuture.get();
        assertEquals(initialBalance, walletRepository.findByUserId(testUserX.getId()).get().getBalance());


        CountDownLatch latch = new CountDownLatch(numDeposits);
        AtomicInteger successfulDeposits = new AtomicInteger(0);

        final List<CompletableFuture<Wallet>> all = new ArrayList<>();
        for (int i = 0; i < numDeposits; i++) {
            TransactionRequest transactionRequest1 = new TransactionRequest(testUserX.getUsername(),null,depositAmount,UUID.randomUUID().toString(),TransactionRequestType.DEPOSIT, 0);
            CompletableFuture<Wallet> v =  walletService.processTransaction(transactionRequest1, true);
            successfulDeposits.incrementAndGet();
            latch.countDown();
            all.add(v);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(all.toArray(new CompletableFuture[0]));
        allFutures.join();

//         Wait for all threads to complete
        int seconds = 100 * numDeposits;
        assertTrue(latch.await(seconds, TimeUnit.SECONDS), "All deposits should complete within timeout");

        // Verify final balance
        Wallet finalWallet = walletRepository.findByUserId(testUserX.getId()).get();
        assertEquals(initialBalance + (numDeposits * depositAmount), finalWallet.getBalance(), 0.001);

        // Verify transaction count
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalWallet.getId());
        // Initial deposit + numDeposits concurrent deposits
        assertThat(transactions).hasSize(1 + numDeposits);
        assertEquals(numDeposits, successfulDeposits.get());
    }



    @Test
    @DisplayName("Should handle concurrent transfers correctly with optimistic locking")
    void shouldHandleConcurrentTransfersCorrectlyWithRetry() throws InterruptedException, ExecutionException {
        int initialBalanceSender = 1000;
        int initialBalanceReceiver = 0;
        int numTransfers = 2;
        double transferAmount = 5.0;

        // Deposit initial funds to sender
        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalanceSender, UUID.randomUUID().toString())), true).get();
        //walletService.deposit(testUser.getId(), initialBalanceSender, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalanceSender, walletRepository.findByUserId(testUser.getId()).get().getBalance());
        assertEquals(initialBalanceReceiver, walletRepository.findByUserId(receiverUser.getId()).get().getBalance());

        ExecutorService executorService = Executors.newFixedThreadPool(numTransfers);
        CountDownLatch latch = new CountDownLatch(numTransfers);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        for (int i = 0; i < numTransfers; i++) {
            final int threadNum = i;
            final String idempotencyKey = UUID.randomUUID().toString();
            executorService.submit(() -> {
                try {
                    CompletableFuture<Wallet> completableFuture =walletService.processTransaction(
                            TransactionMappingService.fromTransferRequest(testUser.getUsername(),
                                    getTransferRequest(receiverUser.getUsername(),transferAmount, idempotencyKey)), true);
                            completableFuture.handle((w,t) -> {if ( t == null) successfulTransfers.incrementAndGet(); return w; });
                            completableFuture.get();
                    ///successfulTransfers.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    System.err.println("Transfer failed for thread " + threadNum + ": Insufficient funds.");
                } catch (Exception e) {
                    System.err.println("Transfer failed for thread " + threadNum + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
//
//        assertTrue(latch.await(5, TimeUnit.SECONDS), "All transfers should complete within timeout");
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Verify final balances
        Wallet finalSenderWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        Wallet finalReceiverWallet = walletRepository.findByUserId(receiverUser.getId()).orElseThrow();

        double expectedSenderBalance = initialBalanceSender - (successfulTransfers.get() * transferAmount);
        double expectedReceiverBalance = initialBalanceReceiver + (successfulTransfers.get() * transferAmount);

        assertEquals(expectedSenderBalance, finalSenderWallet.getBalance(), 0.001);
        assertEquals(expectedReceiverBalance, finalReceiverWallet.getBalance(), 0.001);

        // Verify transaction counts
        // Initial deposit to sender + successfulTransfers (sender view) + successfulTransfers (receiver view)
        List<Transaction> senderTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalSenderWallet.getId());
        List<Transaction> receiverTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalReceiverWallet.getId());

        assertThat(senderTransactions).hasSize(1 + successfulTransfers.get()); // 1 initial deposit + X transfers_sent
        assertThat(receiverTransactions).hasSize(successfulTransfers.get()); // X transfers_received
        assertEquals(numTransfers, successfulTransfers.get(), "All transfers should have successfully completed (assuming enough initial funds).");
    }

    private @NotNull TransferRequest getTransferRequest(String receiverUser,double transferAmount, String idempotencyKey) {
        return new TransferRequest(receiverUser, transferAmount, idempotencyKey);
    }

    private @NotNull WithdrawRequest getWithDrawRequest(double transferAmount, String idempotencyKey) {
        return new WithdrawRequest(transferAmount, idempotencyKey);
    }

    private @NotNull DepositRequest getDepositRequest(double transferAmount, String idempotencyKey) {
        return new DepositRequest(transferAmount, idempotencyKey);
    }

    @Test
    @DisplayName("Should handle concurrent transfers correctly with optimistic locking")
    void shouldHandleConcurrentTransfersCorrectly() throws InterruptedException, ExecutionException {
        int initialBalanceSender = 1000;
        int initialBalanceReceiver = 0;
        int numTransfers = 5;
        double transferAmount = 5.0;

        // Deposit initial funds to sender
        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalanceSender, UUID.randomUUID().toString())), true).get();
        //walletService.deposit(testUser.getId(), initialBalanceSender, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalanceSender, walletRepository.findByUserId(testUser.getId()).get().getBalance());
        assertEquals(initialBalanceReceiver, walletRepository.findByUserId(receiverUser.getId()).get().getBalance());

        CountDownLatch latch = new CountDownLatch(numTransfers);
        AtomicInteger successfulTransfers = new AtomicInteger(0);

        for (int i = 0; i < numTransfers; i++) {
            final int threadNum = i;
            TransactionRequest transactionRequest = new TransactionRequest(testUser.getUsername(),
                    receiverUser.getUsername(),transferAmount,UUID.randomUUID().toString(),TransactionRequestType.TRANSFER, 0);
                try {
                    CompletableFuture<Wallet> v =  walletService.processTransaction(transactionRequest, true);
                    v.get();
                    successfulTransfers.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    System.err.println("Transfer failed for thread " + threadNum + ": Insufficient funds.");
                } catch (Exception e) {
                    System.err.println("Transfer failed for thread " + threadNum + ": " + e.getMessage());
                    e.printStackTrace(); // Print stack trace for unexpected errors
                } finally {
                    latch.countDown();
                }
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "All transfers should complete within timeout");


        // Verify final balances
        Wallet finalSenderWallet = walletRepository.findByUserId(testUser.getId()).get();
        Wallet finalReceiverWallet = walletRepository.findByUserId(receiverUser.getId()).get();

        double expectedSenderBalance = initialBalanceSender - (successfulTransfers.get() * transferAmount);
        double expectedReceiverBalance = initialBalanceReceiver + (successfulTransfers.get() * transferAmount);

        assertEquals(expectedSenderBalance, finalSenderWallet.getBalance(), 0.001);
        assertEquals(expectedReceiverBalance, finalReceiverWallet.getBalance(), 0.001);

        // Verify transaction counts
        // Initial deposit to sender + successfulTransfers (sender view) + successfulTransfers (receiver view)
        List<Transaction> senderTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalSenderWallet.getId());
        List<Transaction> receiverTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalReceiverWallet.getId());

        assertThat(senderTransactions).hasSize(1 + successfulTransfers.get()); // 1 initial deposit + X transfers_sent
        assertThat(receiverTransactions).hasSize(successfulTransfers.get()); // X transfers_received
        assertEquals(numTransfers, successfulTransfers.get(), "All transfers should have successfully completed (assuming enough initial funds).");
    }

    @Test
    @DisplayName("Should prevent transfers exceeding balance under concurrency")
    void shouldPreventOverdraftUnderConcurrencyWithRetry() throws InterruptedException, ExecutionException {
        int initialBalance = 1000; // Small initial balance
        int numConcurrentWithdrawals = 100; // Many attempts to withdraw
        double withdrawalAmount = 150.0; // Small amount per withdrawal

        // Deposit initial funds
        CompletableFuture<Wallet> completableFuture = walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalance, UUID.randomUUID().toString())), true);
        completableFuture.get();
        //walletService.deposit(testUser.getId(), initialBalance, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalance, walletRepository.findByUserId(testUser.getId()).orElseThrow().getBalance());

        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrentWithdrawals);
        CountDownLatch latch = new CountDownLatch(numConcurrentWithdrawals);
        AtomicInteger successfulWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < numConcurrentWithdrawals; i++) {
            final int threadNum = i;
            executorService.submit(() -> {
                String idempotencyKey = UUID.randomUUID().toString();
                try {
                    CompletableFuture<Wallet> completableFuture2 = walletService.processTransaction(TransactionMappingService.fromWithdrawRequest(testUser.getUsername(),getWithDrawRequest(withdrawalAmount, idempotencyKey)), true);
                    completableFuture2.get();
//                    (new WithdrawFundX(new RepoRecord(userRepository,walletRepository,transactionRepository,idempotencyKeyRepository),
//                            new TransactionRequest(testUser.getUsername(),null,withdrawalAmount,idempotencyKey,TransactionRequestType.WITHDRAW,0))).run();
                    //walletService.withdraw(testUser.getId(), withdrawalAmount, idempotencyKey);
                    successfulWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    // This is expected for some operations once balance is depleted
                    System.out.println("Withdrawal failed for thread " + threadNum + ": Insufficient funds.");
                }
                catch (EWalletConcurrentExecutionException | IllegalArgumentException e){
                    System.err.println("Withdrawal failed for thread " + threadNum + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Withdrawal failed for thread " + threadNum + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All withdrawals should complete within timeout");
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Verify final balance is not negative
        Wallet finalWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        // The balance should be initialBalance - (successfulWithdrawals * withdrawalAmount)
        // and should be 0 if all funds are depleted
        double expectedFinalBalance = initialBalance - (successfulWithdrawals.get() * withdrawalAmount);
        assertTrue(finalWallet.getBalance() >= 0); // Balance should never go negative
        assertEquals(expectedFinalBalance, finalWallet.getBalance(), 0.001); // Check against calculated total

        // Verify that the total amount withdrawn doesn't exceed the initial balance
        double totalWithdrawn = successfulWithdrawals.get() * withdrawalAmount;
        assertTrue(totalWithdrawn <= initialBalance);

        // Verify transaction count: initial deposit + successfulWithdrawals
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalWallet.getId());
        assertThat(transactions).hasSize(1 + successfulWithdrawals.get());

        System.out.println("Total successful withdrawals: " + successfulWithdrawals.get());
        System.out.println("Final balance: " + finalWallet.getBalance());
        assertThat(finalWallet.getBalance()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Success rate for withdrawal should be 100%")
    void concurrentWithdrawSuccessRate() throws InterruptedException, ExecutionException {
        int initialBalance = 1000; // Small initial balance
        int numConcurrentWithdrawals = 100; // Many attempts to withdraw
        double withdrawalAmount = 10.0; // Small amount per withdrawal

        // Deposit initial funds
        CompletableFuture<Wallet> completableFuture = walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalance, UUID.randomUUID().toString())), true);
        completableFuture.get();
        //walletService.deposit(testUser.getId(), initialBalance, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalance, walletRepository.findByUserId(testUser.getId()).orElseThrow().getBalance());

        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrentWithdrawals);
        CountDownLatch latch = new CountDownLatch(numConcurrentWithdrawals);
        AtomicInteger successfulWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < numConcurrentWithdrawals; i++) {
            final int threadNum = i;
            executorService.submit(() -> {
                String idempotencyKey = UUID.randomUUID().toString();
                try {
                    CompletableFuture<Wallet> completableFuture2 = walletService.processTransaction(TransactionMappingService.fromWithdrawRequest(testUser.getUsername(),getWithDrawRequest(withdrawalAmount, idempotencyKey)), true);
                    completableFuture2.get();
                    successfulWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    // This is expected for some operations once balance is depleted
                    System.out.println("Withdrawal failed for thread " + threadNum + ": Insufficient funds.");
                }
                catch (EWalletConcurrentExecutionException | IllegalArgumentException e){
                    System.err.println("Withdrawal failed for thread " + threadNum + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Withdrawal failed for thread " + threadNum + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All withdrawals should complete within timeout");
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Verify final balance is not negative
        Wallet finalWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        // The balance should be initialBalance - (successfulWithdrawals * withdrawalAmount)
        // and should be 0 if all funds are depleted
        double expectedFinalBalance = initialBalance - (successfulWithdrawals.get() * withdrawalAmount);
        assertTrue(finalWallet.getBalance() >= 0); // Balance should never go negative
        assertEquals(expectedFinalBalance, finalWallet.getBalance(), 0.001); // Check against calculated total

        // Verify that the total amount withdrawn doesn't exceed the initial balance
        double totalWithdrawn = successfulWithdrawals.get() * withdrawalAmount;
        assertTrue(totalWithdrawn <= initialBalance);

        // Verify transaction count: initial deposit + successfulWithdrawals
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalWallet.getId());
        assertThat(transactions).hasSize(1 + successfulWithdrawals.get());

        System.out.println("Total successful withdrawals: " + successfulWithdrawals.get());
        System.out.println("Final balance: " + finalWallet.getBalance());
        assertEquals(100.0, ((double) successfulWithdrawals.get() /numConcurrentWithdrawals) * 100,0.1);
    }

    @Test
    @DisplayName("Should prevent transfers exceeding balance under concurrency")
    void shouldPreventOverdraftUnderConcurrency() throws InterruptedException, ExecutionException {
        int initialBalance = 100; // Small initial balance
        int numConcurrentWithdrawals = 100; // Many attempts to withdraw
        double withdrawalAmount = 5.0; // Small amount per withdrawal

        // Deposit initial funds
        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalance, UUID.randomUUID().toString())), true).get();
        //walletService.deposit(testUser.getId(), initialBalance, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalance, walletRepository.findByUserId(testUser.getId()).get().getBalance());

        CountDownLatch latch = new CountDownLatch(numConcurrentWithdrawals);
        AtomicInteger successfulWithdrawals = new AtomicInteger(0);

        for (int i = 0; i < numConcurrentWithdrawals; i++) {
            final int threadNum = i;
            TransactionRequest transactionRequest = TransactionMappingService.fromWithdrawRequest(testUser.getUsername(),getWithDrawRequest(withdrawalAmount,UUID.randomUUID().toString()));
            //TransactionRequest transactionRequest = new TransactionRequest(testUser.getId(),null,withdrawalAmount,UUID.randomUUID().toString(),TransactionRequestType.WITHDRAW, 0);

                try {
                    walletService.processTransaction(transactionRequest, true).get();
                    successfulWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    // This is expected for some operations once balance is depleted
                    System.out.println("Withdrawal failed for thread " + threadNum + ": Insufficient funds.");
                } catch (Exception e) {
                    System.err.println("Withdrawal failed for thread " + threadNum + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All withdrawals should complete within timeout");

        // Verify final balance is not negative
        Wallet finalWallet = walletRepository.findByUserId(testUser.getId()).get();
        // The balance should be initialBalance - (successfulWithdrawals * withdrawalAmount)
        // and should be 0 if all funds are depleted
        double expectedFinalBalance = initialBalance - (successfulWithdrawals.get() * withdrawalAmount);
        assertTrue(finalWallet.getBalance() >= 0); // Balance should never go negative
        assertEquals(expectedFinalBalance, finalWallet.getBalance(), 0.001); // Check against calculated total

        // Verify that the total amount withdrawn doesn't exceed the initial balance
        double totalWithdrawn = successfulWithdrawals.get() * withdrawalAmount;
        assertTrue(totalWithdrawn <= initialBalance);

        // Verify transaction count: initial deposit + successfulWithdrawals
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(finalWallet.getId());
        assertThat(transactions).hasSize(1 + successfulWithdrawals.get());

        System.out.println("Total successful withdrawals: " + successfulWithdrawals.get());
        System.out.println("Final balance: " + finalWallet.getBalance());
    }


    @Test
    void testConcurrentTransfers() throws InterruptedException, ExecutionException {
        final int CONCURRENT_REQUESTS = 4;
        // Arrange
        double amount = 20.0;
        // Ensure sender has sufficient funds
        // Deposit initial funds
        double initialBalance = 10000.0;
        CompletableFuture<Wallet> completableFuture = walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser.getUsername(),getDepositRequest(initialBalance, UUID.randomUUID().toString())), true);
        completableFuture.get();
        //walletService.deposit(testUser.getId(), initialBalance, UUID.randomUUID().toString(), 0);
        assertEquals(initialBalance, walletRepository.findByUserId(testUser.getId()).get().getBalance());

        // Ensure receiver exists
        // Act: Execute concurrent transfer requests
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Callable<String>> tasks = getCallables(CONCURRENT_REQUESTS, amount);
        executor.invokeAll(tasks);
//        executor.shutdown();
//        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Assert: Verify that the sender's wallet balance has been correctly updated
        Wallet updatedSenderWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(10000.0 - (CONCURRENT_REQUESTS * amount), updatedSenderWallet.getBalance(), 0.01);

        // Assert: Verify that the receiver's wallet balance has been correctly updated
        Wallet updatedReceiverWallet = walletRepository.findByUserId(receiverUser.getId()).orElseThrow();
        assertEquals(CONCURRENT_REQUESTS * amount, updatedReceiverWallet.getBalance(), 0.01);

        // Assert: Verify that the correct number of transactions were recorded
        assertEquals(CONCURRENT_REQUESTS * 2, transactionRepository.count() - 1);
    }

    private @NotNull List<Callable<String>> getCallables(int CONCURRENT_REQUESTS, double amount) {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            tasks.add(() -> {
                CompletableFuture<Wallet> completableFuture =  walletService.processTransaction(TransactionMappingService
                        .fromTransferRequest(testUser.getUsername(),
                                getTransferRequest(receiverUser.getUsername(), amount, UUID.randomUUID().toString())), true);
                completableFuture.get();
                //while (!completableFuture.isDone()){ System.out.print(" Waiting! ");}
                return "done";
            });
        }
        return tasks;
    }
}
