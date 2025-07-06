package com.ewallet.dom.filter;


import com.ewallet.dom.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.micrometer.core.instrument.config.validate.ValidationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final Map<String,UsernamePasswordAuthenticationToken> map = new HashMap<>();

    @Autowired
    private HandlerExceptionResolver handlerExceptionResolver;
    @Autowired
    private UserDetailsService userDetailsService; // Your UserDetailsService
    @Autowired
    private JwtUtil jwtUtil; // Your JWT utility

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        try {
            final String authorizationHeader = request.getHeader("Authorization");

            String username = null;
            String jwt = null;

            // Check if Authorization header exists and starts with "Bearer "
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                jwt = authorizationHeader.substring(7); // Extract the token string
                if (map.containsKey(jwt) && jwtUtil.validateToken(jwt, (UserDetails) map.get(jwt).getPrincipal())) {
                    loadSecurityContext(map.get(jwt));
                } else {
//                try {
                    username = jwtUtil.extractUsername(jwt); // Extract username from token
//                } catch (IllegalArgumentException e) {
//                    logger.error("Unable to get JWT Token", e);
//                } catch (ExpiredJwtException e) {
//                    logger.error("JWT Token has expired", e);
//                } catch (ValidationException e) {
//                    logger.error("Invalid JWT Signature", e);
//                } catch (MalformedJwtException e) {
//                    logger.error("Malformed JWT Token", e);
//                }
//                }

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
        } catch ( IllegalArgumentException | UsernameNotFoundException |
                  ValidationException | MalformedJwtException |
                  ExpiredJwtException | IOException | ServletException e) {
            log.error("Error while authentication",e);
            handlerExceptionResolver.resolveException(request,response,null,e);
        }
    }

    private void loadSecurityContext(UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken) {
        // Set the authentication in the SecurityContext
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
        //SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

    }

//    @Bean
//    public InitializingBean initializingBean() {
//        return () -> SecurityContextHolder.setStrategyName(
//                SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
//    }




}