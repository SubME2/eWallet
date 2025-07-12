package com.ewallet.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Lombok annotation
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String jwtToken;
    private String message;

    // This exact field name must match what frontend expects
    // You can add more fields here like userId, username, roles, etc. if needed
    // private Long userId;
    // private String username;
}
