package com.ewallet.dom.filter;


import com.ewallet.dom.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.micrometer.core.instrument.config.validate.ValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final Map<String, UsernamePasswordAuthenticationToken> map = new HashMap<>();


    private final HandlerExceptionResolver handlerExceptionResolver;
    private final UserDetailsService userDetailsService; // Your UserDetailsService
    private final JwtUtil jwtUtil; // Your JWT utility

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {

        try {
            final String authorizationHeader = request.getHeader("Authorization");

            String username, jwt;

            // Check if Authorization header exists and starts with "Bearer "
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                jwt = authorizationHeader.substring(7); // Extract the token string
                if (map.containsKey(jwt) && jwtUtil.validateToken(jwt, (UserDetails) map.get(jwt).getPrincipal())) {
                    loadSecurityContext(map.get(jwt));
                } else {
                    username = jwtUtil.extractUsername(jwt); // Extract username from token

                    // If username is found and no authentication is currently set in the SecurityContext
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                        // Validate the token against the user details
                        if (jwtUtil.validateToken(jwt, userDetails)) {
                            // Create an authentication token
                            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            usernamePasswordAuthenticationToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));
                            map.put(jwt, usernamePasswordAuthenticationToken);
                            loadSecurityContext(usernamePasswordAuthenticationToken);
                        }
                    }
                }
            }
            // Continue the filter chain
            chain.doFilter(request, response);
        } catch (IllegalArgumentException | UsernameNotFoundException |
                 ValidationException | MalformedJwtException |
                 ExpiredJwtException | IOException | ServletException e) {
            log.error("Error while authentication", e);
            handlerExceptionResolver.resolveException(request, response, null, e);
        }
    }

    private void loadSecurityContext(UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) {
        // Set the authentication in the SecurityContext
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
    }
}