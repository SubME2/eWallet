package com.ewallet.dom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private Double balance;
    private String message; // For error messages or success messages

    public WalletResponse(Double balance) {
        this.balance = balance;
    }

    public WalletResponse(String message) {
        this.message = message;
    }
}
