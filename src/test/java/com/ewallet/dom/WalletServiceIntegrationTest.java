package com.ewallet.dom;

import com.ewallet.dom.dto.DepositRequest;
import com.ewallet.dom.dto.RegisterRequest;
import com.ewallet.dom.dto.TransferRequest;
import com.ewallet.dom.dto.WithdrawRequest;
import com.ewallet.dom.exception.InsufficientFundsException;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.ewallet.dom.service.AuthService;
import com.ewallet.dom.mapper.TransactionMappingService;
import com.ewallet.dom.service.WalletService;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
@Transactional // Ensures transactions are rolled back after each test
class WalletServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private  EntityManagerFactory emf;

    @Autowired
    private AuthService authService; // To create users for testing

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private User testUser;
    private User receiverUser;

    @BeforeEach
    void setup() {
        // Ensure a clean state for each test if @Transactional isn't enough for complex scenarios
        // For simplicity, we rely on @Transactional which creates a new transaction for each test
        // and rolls it back.

        //deleteAll();
        walletService = new WalletService(userRepository,walletRepository,transactionRepository,idempotencyKeyRepository);

        // Register initial users for tests
        registerTestUsers();

    }

    @Transactional
    private void registerTestUsers() {
        RegisterRequest registerRequest1 = new RegisterRequest();
        registerRequest1.setUsername("testuser_wallet");
        registerRequest1.setPassword("pass123");
        testUser = authService.register(registerRequest1);

        RegisterRequest registerRequest2 = new RegisterRequest();
        registerRequest2.setUsername("receiver_user");
        registerRequest2.setPassword("pass123");
        receiverUser = authService.register(registerRequest2);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @AfterEach
    public void deleteAll() {
        walletRepository.deleteAll();
        transactionRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();
        walletRepository.flush();
        transactionRepository.flush();
        idempotencyKeyRepository.flush();
    }

    @Test
    @DisplayName("Should deposit funds successfully")
    void shouldDepositFundsSuccessfully() throws ExecutionException, InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(100.0, idempotencyKey)), true));
        //Wallet wallet = walletService.deposit(testUser.getId(), 100.0, idempotencyKey, 0);
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertNotNull(wallet);
        assertEquals(100.0, wallet.getBalance());

        // Verify transaction is recorded
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId());
        assertThat(transactions).hasSize(1);
        assertEquals(Transaction.TransactionType.DEPOSIT, transactions.get(0).getType());
        assertEquals(100.0, transactions.get(0).getAmount());

        // Verify idempotency key is recorded
        assertTrue(idempotencyKeyRepository.existsByKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should handle idempotent deposit requests correctly")
    void shouldHandleIdempotentDepositRequests() throws ExecutionException, InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();

        // First deposit
        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(100.0, idempotencyKey)), true).get();
//                .thenRun(() ->{
        //walletService.deposit(testUser.getId(), 100.0, idempotencyKey, 0);
        assertEquals(100.0, walletRepository.findByUserId(testUser.getId()).get().getBalance());
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId())).hasSize(1);

        // Second deposit with the same idempotency key
        CompletableFuture<Wallet> completableFuture = walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(100.0, idempotencyKey)), true);
        completableFuture.get();
        AtomicReference<Wallet> atomicReference = new AtomicReference<>();
         completableFuture.thenRun(() -> atomicReference.set(walletRepository.findByUserId(testUser.getId()).orElseThrow()));
        //Wallet secondDepositWallet = walletService.deposit(testUser.getId(), 100.0, idempotencyKey, 0);

        // Balance should not have changed
        assertEquals(100.0, atomicReference.get().getBalance());
        // No new transaction should be recorded
        assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId())).hasSize(1);
//                });
    }

    @Test
    @DisplayName("Should withdraw funds successfully")
    void shouldWithdrawFundsSuccessfully() {
        // First deposit some funds
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(200.0, UUID.randomUUID().toString())), true));
        //walletService.deposit(testUser.getId(), 200.0, UUID.randomUUID().toString(), 0);

        String idempotencyKey = UUID.randomUUID().toString();
        handleException(walletService.processTransaction(TransactionMappingService
                .fromWithdrawRequest(testUser,getWithDrawRequest(50.0, idempotencyKey)), true));
        //Wallet wallet = walletService.withdraw(testUser.getId(), 50.0, idempotencyKey);
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();

        assertNotNull(wallet);
        assertEquals(150.0, wallet.getBalance()); // 200 - 50

        // Verify transaction is recorded (1 deposit + 1 withdrawal)
        List<Transaction> transactions = transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId());
        assertThat(transactions).hasSize(2);
        assertEquals(Transaction.TransactionType.WITHDRAWAL, transactions.get(0).getType());
        assertEquals(50.0, transactions.get(0).getAmount());

        // Verify idempotency key is recorded
        assertTrue(idempotencyKeyRepository.existsByKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should handle idempotent withdrawal requests correctly")
    void shouldHandleIdempotentWithdrawalRequests() {
        // First deposit some funds
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(200.0, UUID.randomUUID().toString())), true));
        //walletService.deposit(testUser.getId(), 200.0, UUID.randomUUID().toString(), 0);

        String idempotencyKey = UUID.randomUUID().toString();

        // First withdrawal
        TransactionRequest transactionRequest = TransactionMappingService
                .fromWithdrawRequest(testUser, getWithDrawRequest(50.0, idempotencyKey));
        handleException(walletService.processTransaction(transactionRequest, true));
        //walletService.withdraw(testUser.getId(), 50.0, idempotencyKey);
        assertEquals(150.0, walletRepository.findByUserId(testUser.getId()).get().getBalance());
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId())).hasSize(2); // 1 deposit + 1 withdrawal

        // Second withdrawal with the same idempotency key

        handleException(walletService.processTransaction(transactionRequest, true));

//         Balance should not have changed

        Wallet secondWithdrawalWallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        assertEquals(150.0, secondWithdrawalWallet.getBalance());
//         No new transaction should be recorded
        assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(secondWithdrawalWallet.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException on withdrawal if balance is low")
    void shouldThrowInsufficientFundsExceptionOnWithdrawal() {
        String idempotencyKey = UUID.randomUUID().toString();
        // User starts with 0.0 balance

        InsufficientFundsException thrown = assertThrows(InsufficientFundsException.class, () -> {
            handleException(walletService
                    .processTransaction(TransactionMappingService
                            .fromWithdrawRequest(testUser,getWithDrawRequest(50.0, idempotencyKey)), true));
            //walletService.withdraw(testUser.getId(), 50.0, idempotencyKey);
        });

        assertThat(thrown.getMessage()).contains("Insufficient funds for withdrawal.");
        // Ensure idempotency key is NOT recorded if transaction fails
        assertFalse(idempotencyKeyRepository.existsByKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should transfer funds successfully between users")
    void shouldTransferFundsSuccessfully() throws ExecutionException, InterruptedException {
        // Deposit funds to sender

        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(500.0, UUID.randomUUID().toString())), true);
        //walletService.deposit(testUser.getId(), 500.0, UUID.randomUUID().toString(), 0);

        String idempotencyKey = UUID.randomUUID().toString();
        walletService.processTransaction(
                TransactionMappingService.fromTransferRequest(testUser,
                        getTransferRequest(receiverUser.getUsername(), 100.0, idempotencyKey)), true)
                .thenRun(()->{

        // Verify balances
        assertEquals(400.0, walletRepository.findByUserId(testUser.getId()).get().getBalance()); // 500 - 100
        assertEquals(100.0, walletRepository.findByUserId(receiverUser.getId()).get().getBalance()); // 0 + 100
        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        // Verify sender's transactions (1 deposit + 1 transfer_sent)
        List<Transaction> senderTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId());
        assertThat(senderTransactions).hasSize(2);
        assertEquals(Transaction.TransactionType.TRANSFER_SENT, senderTransactions.get(0).getType());
        assertEquals(100.0, senderTransactions.get(0).getAmount());
        assertEquals(receiverUser.getUsername(), senderTransactions.get(0).getReceiverUsername());
        assertEquals(testUser.getUsername(), senderTransactions.get(0).getSenderUsername());


        // Verify receiver's transactions (1 transfer_received)
        Wallet walletReceiver = walletRepository.findByUserId(receiverUser.getId()).orElseThrow();
        List<Transaction> receiverTransactions = transactionRepository.findByWalletIdOrderByTimestampDesc(walletReceiver.getId());
        assertThat(receiverTransactions).hasSize(1);
        assertEquals(Transaction.TransactionType.TRANSFER_RECEIVED, receiverTransactions.get(0).getType());
        assertEquals(100.0, receiverTransactions.get(0).getAmount());
        assertEquals(receiverUser.getUsername(), receiverTransactions.get(0).getReceiverUsername());
        assertEquals(testUser.getUsername(), receiverTransactions.get(0).getSenderUsername());

        // Verify idempotency key is recorded
        assertTrue(idempotencyKeyRepository.existsByKey(idempotencyKey));
                });
    }

    @Test
    @DisplayName("Should handle idempotent transfer requests correctly")
    void shouldHandleIdempotentTransferRequests() throws ExecutionException, InterruptedException {
        // Deposit funds to sender
        walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(500.0, UUID.randomUUID().toString())), true);
        //walletService.deposit(testUser.getId(), 500.0, UUID.randomUUID().toString(), 0);

        String idempotencyKey = UUID.randomUUID().toString();

        // First transfer
        walletService.processTransaction(
                        TransactionMappingService.fromTransferRequest(testUser,
                                getTransferRequest(receiverUser.getUsername(), 100.0, idempotencyKey)), true)
                .thenRun(() -> {

                    assertEquals(400.0, walletRepository.findByUserId(testUser.getId()).get().getBalance());
                    assertEquals(100.0, walletRepository.findByUserId(receiverUser.getId()).get().getBalance());
                    Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
                    Wallet walletReciever = walletRepository.findByUserId(receiverUser.getId()).orElseThrow();
                    assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId())).hasSize(2); // D+TS
                    assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(walletReciever.getId())).hasSize(1); // TR

                    // Second transfer with the same idempotency key
                    walletService.processTransaction(
                            TransactionMappingService.fromTransferRequest(testUser,
                                    getTransferRequest(receiverUser.getUsername(), 100.0, idempotencyKey)), true);


                    // Balances should not have changed from the first transfer
                    assertEquals(400.0, walletRepository.findByUserId(testUser.getId()).get().getBalance());
                    assertEquals(100.0, walletRepository.findByUserId(receiverUser.getId()).get().getBalance());
                    // No new transactions should be recorded
                    assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(wallet.getId())).hasSize(2);
                    assertThat(transactionRepository.findByWalletIdOrderByTimestampDesc(walletReciever.getId())).hasSize(1);
                });
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException on transfer if sender balance is low")
    void shouldThrowInsufficientFundsExceptionOnTransfer() {
        // testUser has 0.0 balance initially

        String idempotencyKey = UUID.randomUUID().toString();
        assertNotNull(userRepository.findById(testUser.getId()).orElseThrow());
        InsufficientFundsException thrown = assertThrows(InsufficientFundsException.class, () -> {

            handleException(walletService.processTransaction(
                        TransactionMappingService.fromTransferRequest(testUser,
                                getTransferRequest(receiverUser.getUsername(), 100.0, idempotencyKey)), true));

                   // .thenRun(() -> {log.debug("done");});
        });

        assertThat(thrown.getMessage()).contains("Insufficient funds for transfer.");
        // Ensure idempotency key is NOT recorded if transaction fails
        assertFalse(idempotencyKeyRepository.existsByKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if transfer to self")
    void shouldThrowIllegalArgumentExceptionIfTransferToSelf() {
        // Deposit some funds to testUser
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(100.0, UUID.randomUUID().toString())), true));
        //walletService.deposit(testUser.getId(), 100.0, UUID.randomUUID().toString(), 0);

        String idempotencyKey = UUID.randomUUID().toString();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            handleException(walletService.processTransaction(
                    TransactionMappingService.fromTransferRequest(testUser,
                            getTransferRequest(testUser.getUsername(), 100.0, idempotencyKey)), true));
        });

        assertThat(thrown.getMessage()).contains("Cannot transfer funds to yourself.");
        // Ensure idempotency key is NOT recorded if transaction fails
        assertFalse(idempotencyKeyRepository.existsByKey(idempotencyKey));
    }

    @Test
    @DisplayName("Should return wallet balance for user")
    void shouldReturnWalletBalanceForUser() {
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(75.0, UUID.randomUUID().toString())), true));
        //walletService.deposit(testUser.getId(), 75.0, UUID.randomUUID().toString(), 0);
        Wallet wallet = walletService.findWalletByUserID(testUser.getId());
        assertEquals(75.0, wallet.getBalance());
    }

    @Test
    @DisplayName("Should retrieve transactions for a wallet")
    void shouldRetrieveTransactionsForWallet() throws ExecutionException, InterruptedException {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        handleException(walletService.processTransaction(TransactionMappingService
                .fromDepositRequest(testUser, getDepositRequest(100.0, key1)), true));
        //walletService.deposit(testUser.getId(), 100.0, key1, 0);

        handleException(walletService.processTransaction(TransactionMappingService
                        .fromWithdrawRequest(testUser,getWithDrawRequest(20.0, key2)), true));
//                .thenRun(()->{
       // walletService.withdraw(testUser.getId(), 20.0, key2);

        Wallet wallet = walletRepository.findByUserId(testUser.getId()).orElseThrow();
        List<Transaction> transactions = walletService.getTransactionsForWallet(wallet.getId());

        assertThat(transactions).hasSize(2);
        // Assert order (latest first due to OrderByTimestampDesc)
        assertEquals(Transaction.TransactionType.WITHDRAWAL, transactions.get(0).getType());
        assertEquals(Transaction.TransactionType.DEPOSIT, transactions.get(1).getType());
//                });
    }

    private @NotNull WithdrawRequest getWithDrawRequest(double transferAmount, String idempotencyKey) {
        return new WithdrawRequest(transferAmount, idempotencyKey);
    }

    private @NotNull TransferRequest getTransferRequest(String receiverUser, double transferAmount, String idempotencyKey) {
        return new TransferRequest(receiverUser, transferAmount, idempotencyKey);
    }

    private @NotNull DepositRequest getDepositRequest( double transferAmount, String idempotencyKey) {
        return new DepositRequest( transferAmount, idempotencyKey);
    }

    private void handleException(CompletableFuture<?> completableFuture) {
        try {
            completableFuture.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if ( e.getCause() instanceof  InsufficientFundsException insufficientFundsException) throw insufficientFundsException;
            else if (e.getCause() instanceof IllegalArgumentException illegalArgumentException)  throw illegalArgumentException;
            else throw new RuntimeException(e);
        }
    }
}
