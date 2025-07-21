package com.ultracards.webui.controllers;

import com.ultracards.webui.dto.AuthResponseDTO;
import com.ultracards.webui.dto.EmailRequestDTO;
import com.ultracards.webui.dto.VerifyCodeRequestDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Controller for authentication-related pages.
 * Handles login, sign up, and verification processes.
 * 
 * This controller connects to the server module for authentication.
 * It uses the following server endpoints:
 * - POST /auth/verify - Verifies a code and returns a JWT token
 * - POST /auth/refresh - Refreshes a JWT token (handled by the server)
 * - POST /auth/logout - Logs out a user
 */
@Controller
@RequestMapping("/auth")
public class AuthController extends BaseController {

    private final RestTemplate restTemplate;
    private final String serverBaseUrl;

    @Autowired
    public AuthController(RestTemplate restTemplate, String serverBaseUrl) {
        this.restTemplate = restTemplate;
        this.serverBaseUrl = serverBaseUrl;
    }

    /**
     * Handles GET requests to the auth page.
     * Renders the login/sign up view.
     * Redirects logged-in users to the home page.
     *
     * @param model The model to add attributes to
     * @param request The HTTP request
     * @return The name of the view to render or a redirect
     */
    @GetMapping
    public String authPage(Model model, HttpServletRequest request) {
        // Check if user is already authenticated
        HttpSession session = request.getSession(false);
        if (isAuthenticated(session)) {
            // Redirect to home page if already logged in
            return "redirect:/";
        }
        
        // No additional model attributes needed for initial auth page load
        return "auth";
    }
    
    /**
     * Handles POST requests for email verification.
     * Sends a request to the server to generate and send a verification code to the provided email.
     * Returns an error if the user is already authenticated.
     *
     * @param email The email to send the verification code to
     * @param model The model to add attributes to
     * @param session The HTTP session
     * @return JSON response indicating success or failure
     */
    @PostMapping("/send-code")
    @ResponseBody
    public String sendVerificationCode(@RequestParam("email") String email, Model model, HttpSession session) {
        // Check if user is already authenticated
        if (isAuthenticated(session)) {
            return "{\"success\": false, \"message\": \"You are already logged in.\"}";
        }
        try {
            // Store email in session for the verification step
            session.setAttribute("pendingEmail", email);
            
            // Create a request DTO with the email
            var requestDTO = new EmailRequestDTO(email);
            
            try {
                // Try to authorize (for existing users)
                restTemplate.postForEntity(
                        serverBaseUrl + "/auth/authorize",
                        requestDTO,
                        Void.class);
                
                // Return success response
                return "{\"success\": true, \"message\": \"Verification code sent to " + email + "\"}";
            } catch (HttpClientErrorException e) {
                // If login fails, the user might not exist
                // We would need to register them, but we don't have a username yet
                // For simplicity, we'll just return an error message
                return "{\"success\": false, \"message\": \"User not found. Please register first.\"}";
            }
        } catch (Exception e) {
            // Return error response
            return "{\"success\": false, \"message\": \"Failed to send verification code: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Handles POST requests for code verification.
     * Sends a request to the server to verify the code and authenticate the user.
     * Redirects already authenticated users to the home page.
     *
     * @param email The email that was verified
     * @param verificationCode The verification code entered by the user
     * @param model The model to add attributes to
     * @param session The HTTP session to store authentication data
     * @param response The HTTP response to set cookies
     * @return A redirect to the game selection page or back to auth page on error
     */
    @PostMapping("/complete")
    public String completeAuth(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam("verificationCode") String verificationCode,
            Model model,
            HttpSession session,
            HttpServletResponse response) {
        
        // Check if user is already authenticated
        if (isAuthenticated(session)) {
            return "redirect:/";
        }
        
        // Get email from session if not provided in request
        if (email == null || email.trim().isEmpty()) {
            email = (String) session.getAttribute("pendingEmail");
            if (email == null || email.trim().isEmpty()) {
                addErrorMessage(model, "Email is required");
                return "auth";
            }
        }
        
        // Validate input
        if (verificationCode == null || verificationCode.trim().isEmpty()) {
            addErrorMessage(model, "Please enter a verification code");
            model.addAttribute("email", email);
            return "auth";
        }
        
        try {
            // Verify code and get authentication token
            VerifyCodeRequestDTO requestDTO = new VerifyCodeRequestDTO(email, verificationCode);
            ResponseEntity<AuthResponseDTO> responseEntity = restTemplate.postForEntity(
                    serverBaseUrl + "/auth/verify",
                    requestDTO,
                    AuthResponseDTO.class);
            
            AuthResponseDTO authResponse = responseEntity.getBody();
            if (authResponse != null) {
                // Store authentication data in session
                session.setAttribute("token", authResponse.getToken());
                session.setAttribute("email", authResponse.getEmail());
                session.setAttribute("username", authResponse.getUsername());
                session.setAttribute("role", authResponse.getRole());
                session.setAttribute("authenticated", true);
                
                // Copy any cookies from the response
                if (responseEntity.getHeaders().containsKey(HttpHeaders.SET_COOKIE)) {
                    for (String cookie : responseEntity.getHeaders().get(HttpHeaders.SET_COOKIE)) {
                        response.addHeader(HttpHeaders.SET_COOKIE, cookie);
                    }
                }
                
                // Check if username is empty
                String username = authResponse.getUsername();
                if (username == null || username.trim().isEmpty()) {
                    // Show username input field
                    model.addAttribute("email", email);
                    model.addAttribute("needUsername", true);
                    return "auth";
                }
                
                // Redirect to game selection page after successful authentication
                return "redirect:/games";
            } else {
                addErrorMessage(model, "Authentication failed: No response from server");
            }
        } catch (HttpClientErrorException e) {
            addErrorMessage(model, "Authentication failed: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            addErrorMessage(model, "Authentication failed: " + e.getMessage());
        }
        
        // If we get here, there was an error
        model.addAttribute("email", email);
        return "auth";
    }

    /**
     * Handles POST requests for setting a username.
     * Sends a request to the server to update the user's username.
     * This method is only for users who are in the process of authentication.
     * If the user is already fully authenticated (with a username), they are redirected to the home page.
     *
     * @param email The email of the user
     * @param username The new username to set
     * @param model The model to add attributes to
     * @param session The HTTP session to update
     * @return A redirect to the game selection page or back to auth page on error
     */
    @PostMapping("/set-username")
    public String setUsername(
            @RequestParam("email") String email,
            @RequestParam("username") String username,
            Model model,
            HttpSession session) {
        // Check if user is already fully authenticated with a username
        if (isAuthenticated(session) && session.getAttribute("username") != null 
                && !((String)session.getAttribute("username")).isEmpty()) {
            return "redirect:/";
        }
        
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            addErrorMessage(model, "Please enter a username");
            model.addAttribute("email", email);
            model.addAttribute("needUsername", true);
            return "auth";
        }
        
        try {
            // Create a request DTO with the email and username
            EmailRequestDTO requestDTO = new EmailRequestDTO(email, username);
            
            // Send request to server to update username
            restTemplate.postForEntity(
                    serverBaseUrl + "/auth/set-username",
                    requestDTO,
                    Void.class);
            
            // Update username in session
            session.setAttribute("username", username);
            
            // Redirect to game selection page after successful username update
            return "redirect:/games";
        } catch (Exception e) {
            addErrorMessage(model, "Failed to set username: " + e.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("needUsername", true);
            return "auth";
        }
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Get refresh token cookie
            Cookie[] cookies = request.getCookies();
            String refreshToken = null;
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                        break;
                    }
                }
            }
            
            // Send logout request to server
            if (refreshToken != null) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Cookie", "refreshToken=" + refreshToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                restTemplate.exchange(
                        serverBaseUrl + "/auth/logout",
                        HttpMethod.POST,
                        entity,
                        Void.class);
            }
        } catch (Exception e) {
            // Log error but continue with logout
            System.err.println("Error during logout: " + e.getMessage());
        } finally {
            // Clear session
            session.invalidate();
            
            // Clear cookies
            Cookie cookie = new Cookie("refreshToken", "");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
        
        return "redirect:/";
    }
}