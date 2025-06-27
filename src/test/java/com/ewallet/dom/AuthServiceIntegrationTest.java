package com.ewallet.dom;

import com.ewallet.dom.dto.RegisterRequest;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.ewallet.dom.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // Use @SpringBootTest to load the full application context
@Transactional // Rolls back transactions after each test
class AuthServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        // Clear repository before each test, though @Transactional handles most of it.
        // For more complex scenarios, you might need a clean-up method here.
    }

    @Test
    @DisplayName("Should register a new user successfully")
    void shouldRegisterNewUserSuccessfully() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("securepassword");

        User registeredUser = authService.register(request);

        assertNotNull(registeredUser.getId());
        assertEquals("testuser", registeredUser.getUsername());
        assertTrue(passwordEncoder.matches("securepassword", registeredUser.getPassword()));
        Wallet wallet = walletRepository.findByUserId(registeredUser.getId()).orElseThrow();
        assertNotNull(wallet);
        assertEquals(0.0, wallet.getBalance());

        // Verify it's persisted in the database
        User foundUser = userRepository.findByUsername("testuser").orElse(null);
        assertNotNull(foundUser);
        assertEquals(registeredUser.getId(), foundUser.getId());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if username already exists")
    void shouldThrowExceptionIfUsernameExists() {
        // Register once
        RegisterRequest firstRequest = new RegisterRequest();
        firstRequest.setUsername("existinguser");
        firstRequest.setPassword("password123");
        authService.register(firstRequest);

        // Try to register again with the same username
        RegisterRequest secondRequest = new RegisterRequest();
        secondRequest.setUsername("existinguser");
        secondRequest.setPassword("anotherpassword");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            authService.register(secondRequest);
        });

        assertThat(thrown.getMessage()).contains("Username already taken.");
    }

    @Test
    @DisplayName("Should find user by username")
    void shouldFindUserByUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("findmeuser");
        request.setPassword("somepass");
        User registeredUser = authService.register(request);

        User foundUser = authService.findByUsername("findmeuser");

        assertNotNull(foundUser);
        assertEquals(registeredUser.getId(), foundUser.getId());
        assertEquals("findmeuser", foundUser.getUsername());
    }

    @Test
    @DisplayName("Should throw RuntimeException if user not found by username")
    void shouldThrowExceptionIfUserNotFound() {
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            authService.findByUsername("nonexistentuser");
        });

        assertThat(thrown.getMessage()).contains("User not found.");
    }
}
