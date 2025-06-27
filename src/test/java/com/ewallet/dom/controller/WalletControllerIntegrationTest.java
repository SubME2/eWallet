package com.ewallet.dom.controller;


import com.ewallet.dom.BaseIntegrationTest;
import com.ewallet.dom.config.SecurityConfig;
import com.ewallet.dom.dto.DepositRequest;
import com.ewallet.dom.dto.TransferRequest;
import com.ewallet.dom.dto.WithdrawRequest;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(SecurityConfig.class)
@AutoConfigureMockMvc
@Transactional
class WalletControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Wallet testUserWallet;

    @BeforeEach
    void setUp() {
        // Clean up to ensure test isolation

        persistUser();

        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()) // Include Spring Security filters
                .build();
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

    @Transactional
    private void persistUser() {
        // Arrange: Create a user and wallet for tests that need an authenticated user
        testUser = getUser("testuser", "password");
        userRepository.save(testUser);
        testUserWallet = new Wallet(testUser, 100.0); // Initial balance of 100.0
        walletRepository.save(testUserWallet);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    @WithMockUser(username = "testuser")
    void getBalance_shouldReturnWalletBalance() throws Exception {
        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    @WithMockUser(username = "testuser")
    void deposit_shouldIncreaseBalance() throws Exception {
        DepositRequest depositRequest = new DepositRequest(50.0, UUID.randomUUID().toString());

        MvcResult mvcResult = mockMvc.perform(post("/api/wallet/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.0));

        Wallet updatedWallet = walletRepository.findById(testUserWallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualTo(150.0);
    }

    @Test
    @WithMockUser(username = "testuser")
    void withdraw_shouldDecreaseBalance() throws Exception {
        WithdrawRequest withdrawRequest = new WithdrawRequest(30.0, UUID.randomUUID().toString());

        MvcResult mvcResult = mockMvc.perform(post("/api/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withdrawRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.0));

        Wallet updatedWallet = walletRepository.findById(testUserWallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualTo(70.0);
    }


    @Test
    @WithMockUser(username = "testuser")
    void transfer_shouldMoveFundsBetweenWallets() throws Exception {
        // Arrange: Create a receiver user and wallet
        User receiverUser = getUser("receiver", "password");
        userRepository.save(receiverUser);
        Wallet receiverWallet = new Wallet(receiverUser, 20.0);
        walletRepository.save(receiverWallet);

        TransferRequest transferRequest = new TransferRequest("receiver", 50.0, UUID.randomUUID().toString());

        MvcResult mvcResult = mockMvc.perform(post("/api/wallet/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Assert sender's wallet is updated
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.0));

        // Assert receiver's wallet is updated
        Wallet updatedReceiverWallet = walletRepository.findById(receiverWallet.getId()).orElseThrow();
        assertThat(updatedReceiverWallet.getBalance()).isEqualTo(70.0);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getTransactions_shouldReturnListOfTransactions() throws Exception {
        // Arrange: Perform some transactions to have a history
        deposit_shouldIncreaseBalance(); // Reuse deposit test to create a transaction

        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].amount").value(50.0))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"));
    }

    private @NotNull User getUser(String existinguser, String password) {
        User user = new User();
        user.setUsername(existinguser);
        user.setPassword(passwordEncoder.encode(password));
        return user;
    }
}

