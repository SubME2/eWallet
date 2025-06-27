package com.ewallet.dom.controller;

import com.ewallet.dom.BaseIntegrationTest;
import com.ewallet.dom.dto.LoginRequest;
import com.ewallet.dom.dto.RegisterRequest;
import com.ewallet.dom.model.User;
import com.ewallet.dom.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;


@AutoConfigureMockMvc
@Transactional // Rollback transactions after each test
public class AuthControllerIntegrationTest  extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Clean up the repository before each test to ensure isolation
    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerUser_whenValidRequest_shouldCreateUserAndReturnCreated() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().string("User registered successfully: testuser"));

        User user = userRepository.findByUsername("testuser").orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("testuser");
    }

    @Test
    void registerUser_whenUsernameExists_shouldReturnBadRequest() throws Exception {
        // Arrange: Create a user first
        userRepository.save(getUser("existinguser", "password"));

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("existinguser");
        registerRequest.setPassword("newpassword");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Username already taken."));
    }


    @Test
    void authenticateUser_whenValidCredentials_shouldReturnOk() throws Exception {
        // Arrange: Create and save a user
        userRepository.save(getUser("testuser", "password123"));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("User logged in successfully!"));
    }

    @Test
    void authenticateUser_whenInvalidPassword_shouldReturnUnauthorized() throws Exception {
        // Arrange: Create and save a user
        userRepository.save(getUser("testuser", "password123"));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid username or password."));
    }

    private @NotNull User getUser(String existinguser, String password) {
        User user = new User();
        user.setUsername(existinguser);
        user.setPassword(passwordEncoder.encode(password));
        return user;
    }
}
