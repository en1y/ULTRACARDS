package com.ultracards.server.controllers.auth;

import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.EmailService;
import com.ultracards.server.service.auth.AuthService;
import com.ultracards.server.service.auth.SessionService;
import com.ultracards.server.service.auth.TokenService;
import com.ultracards.server.service.auth.VerificationCodeService;
import com.ultracards.server.service.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UserService userService = mock(UserService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService, mock(TokenService.class), userService,
                    mock(SessionService.class), mock(EmailService.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

    @Test
    void invalidEmailIsRejectedBeforeAnyUserIsCreated() throws Exception {
        String[] bodies = {"{}", "{\"email\":\"\"}", "{\"email\":\"not-an-email\"}"};
        for (var body : bodies) {
            mockMvc.perform(post("/api/auth/email/send")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(userService, authService);
    }

    @Test
    void codeWithNoPendingVerificationIsRejectedNotAServerError() throws Exception {
        var verificationCodeService = mock(VerificationCodeService.class);
        var realAuthService = new AuthService(verificationCodeService, userService,
                mock(UserRepository.class), mock(EmailService.class), mock(SessionService.class));
        var mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(realAuthService, mock(TokenService.class), userService,
                        mock(SessionService.class), mock(EmailService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }
}
