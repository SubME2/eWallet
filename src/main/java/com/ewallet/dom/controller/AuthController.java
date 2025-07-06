package com.ewallet.dom.controller;


import com.ewallet.dom.dto.AuthResponse;
import com.ewallet.dom.dto.LoginRequest;
import com.ewallet.dom.dto.RegisterRequest;
import com.ewallet.dom.model.User;
import com.ewallet.dom.service.AuthService;
import com.ewallet.dom.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            User registeredUser = authService.register(registerRequest);
            return new ResponseEntity<>("User registered successfully: " + registeredUser.getUsername(), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("Registration failed due to an unexpected error.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // 1. Authenticate the user using Spring Security's AuthenticationManager
            // This will throw BadCredentialsException if username/password are incorrect
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            // 2. If authentication is successful, get UserDetails from the authenticated object
            // The principal object contains the authenticated UserDetails
            final UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            // 3. Generate the JWT token using your JwtUtil
            final String jwt = jwtUtil.generateToken(userDetails);

            // 4. Return the JWT token in a custom AuthResponse DTO
            return ResponseEntity.ok(new AuthResponse(jwt,"Login successful"));

        } catch (Exception e) {
            return new ResponseEntity<>(new AuthResponse(null,"Invalid username or password."), HttpStatus.UNAUTHORIZED);
        }
    }
}
