package com.ewallet.dom.config;


import com.ewallet.dom.model.User;
import com.ewallet.dom.repository.UserRepository;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CachingUserDetailsService;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
// For future JWT

//@EnableRetry
@EnableJpaAuditing
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@SecurityScheme(name = "basicAuth", // This is the name you'll reference later
        type = SecuritySchemeType.HTTP, scheme = "basic")
public class SecurityConfig {

    private final UserRepository userRepository; // Use AuthService to load user details

    private static final String AUTH_PATH = "/api/auth/**";
    private static final String[] AUTH_WHITELIST = {
            "/swagger-ui/index.html",
            "/swagger-resources/**",
            "/v3/api-docs",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for API-based apps
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_PATH).permitAll() // Allow registration and login
                        .requestMatchers(AUTH_WHITELIST).permitAll() // Swagger
                        .anyRequest().authenticated() // All other requests require authentication
                )
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/api/auth/**").permitAll()
//                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // Require ADMIN role
//                        .anyRequest().authenticated()
//                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Use stateless sessions (good for REST APIs)
                )
                .httpBasic(Customizer.withDefaults()); // Use HTTP Basic for simplicity in this example. For production, consider JWT.

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                @Override public String getUsername() {
                    return user.getUsername();
                }
            };
        });
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found."));
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}