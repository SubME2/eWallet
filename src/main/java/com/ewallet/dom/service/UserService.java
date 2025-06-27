package com.ewallet.dom.service;

import com.ewallet.dom.model.User;
import com.ewallet.dom.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;


    @Transactional
    public User getUserByName(@NotNull String name){
        return userRepository.findByUsername(name).orElseThrow();
    }


}
