package com.ewallet.dom.config;


import com.ewallet.dom.filter.JwtRequestFilter;
import com.ewallet.dom.repository.UserRepository;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
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


    @Autowired
    private UserDetailsService userDetailsService; // Your UserDetailsService

    private final JwtRequestFilter jwtRequestFilter;

    private static final String AUTH_PATH = "/api/auth/**";
    private static final String[] AUTH_WHITELIST = {
            "/swagger-ui/index.html",
            "/swagger-resources/**",
            "/v3/api-docs",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**"
    };

    @Value("${spring.websecurity.debug:false}")
    boolean webSecurityDebug;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.debug(webSecurityDebug);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                // Enable CORS with the configuration defined above
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for API-based apps
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // This is invoked when an unauthenticated user tries to access a protected resource.
                            // We send a 401 Unauthorized response.
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(AUTH_PATH).permitAll() // Allow registration and login
                        .requestMatchers(AUTH_WHITELIST).permitAll() // Swagger
                        .requestMatchers("/error").permitAll()
                        //.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll() // Do not use this fix may create security whole
                        .anyRequest().authenticated() // All other requests require authentication
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Use stateless sessions (good for REST APIs)
                )
                //.httpBasic(Customizer.withDefaults())
        ; // Use HTTP Basic for simplicity in this example. For production, consider JWT.
        // This the security fix for @AsyncEnable application
        http.securityContext(sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()));

        // Add our custom JWT filter before Spring Security's UsernamePasswordAuthenticationFilter
        // This ensures our filter processes the JWT before Spring's default authentication flow.
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        // Set the custom authentication provider
        http.authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }





    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }


    // 4. CORS Configuration Bean (Crucial for React frontend)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow requests from your React frontend's origin
        configuration.setAllowedOrigins(List.of("http://localhost:3000")); // Adjust if your frontend runs on a different port/domain
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization","authorization", "Content-Type", "Cache-Control", "X-Requested-With", "Access-Control-Allow-Origin"));
        configuration.setAllowCredentials(true); // Allow cookies/authentication headers
        configuration.setMaxAge(3600L); // How long the pre-flight request can be cached
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this CORS config to all paths
        return source;
    }

}