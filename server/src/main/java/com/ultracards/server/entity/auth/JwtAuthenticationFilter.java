package com.ultracards.server.entity.auth;

import com.ultracards.server.repositories.UserRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    @Value("${app.jwt.secret.token}")
    private String JWT_SECRET;

    public JwtAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            var token = authHeader.substring(7);
            try {
                var claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                var email = claims.getSubject();
                if (email != null) {
                    // Load user from database
                    var user = userRepository.findByEmail(email).orElse(null);
                    if (user != null) {
                        // Get roles from either token claims or user object
                        var authorities = new ArrayList<SimpleGrantedAuthority>();
                        var rolesClaim = claims.get("role");
                        if (rolesClaim != null) {
                            var rolesStr = rolesClaim.toString();
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + rolesStr));
                        } else {
                            // Fallback: use roles from UserEntity if claim not present
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                        }
                        // Create Authentication token
                        var authToken =
                                new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Log the exception but don't expose it to the client
                // This prevents information leakage about the token validation process
                SecurityContextHolder.clearContext();
                // Continue with the filter chain without authentication
            }
        }
        filterChain.doFilter(request, response);
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}
