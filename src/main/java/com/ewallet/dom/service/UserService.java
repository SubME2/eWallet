package com.ewallet.dom.service;

import com.ewallet.dom.model.User;
import com.ewallet.dom.repository.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.CachingUserDetailsService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;


    @Transactional
    public User getUserByName(@NotNull String name){
        return userRepository.findByUsername(name).orElseThrow();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new CachingUserDetailsService(username -> {
            User user= getUser( username);
            return new UserDetails() {
                @Override  public Collection<? extends GrantedAuthority> getAuthorities() {
                    return List.of();
                }
                @Override  public String getPassword() {
                    return user.getPassword();
                }
                @Override  public String getUsername() {
                    return user.getUsername();
                }
            };
        });
    }

}
