package com.ultracards.server.controllers.users;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.server.controllers.errors.ApiExceptionHandler;
import com.ultracards.server.service.users.ProfileService;
import com.ultracards.server.service.users.UserSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSearchControllerTest {

    private final UserSearchService userSearchService = mock(UserSearchService.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new UserSearchController(userSearchService, profileService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void validUsernameRequestReturnsProfiles() throws Exception {
        var profile = new ProfileDTO();
        profile.setId(1L);
        profile.setUsername("Alice");
        profile.setRoles(List.of("USER"));

        when(userSearchService.searchUsersByUsername("Alice", 0, 50)).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/users/search/username/{username}", "Alice")
                        .param("lower", "0")
                        .param("higher", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("Alice"))
                .andExpect(jsonPath("$[0].email").isEmpty())
                .andExpect(jsonPath("$[0].roles[0]").value("USER"));

        verify(userSearchService).searchUsersByUsername("Alice", 0, 50);
    }

    @Test
    void validIdRequestReturnsProfiles() throws Exception {
        var profile = new ProfileDTO();
        profile.setId(12L);
        profile.setUsername("Alice");
        profile.setRoles(List.of("USER"));

        when(userSearchService.searchUsersById("1", 0, 50)).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/users/search/id/{id}", "1")
                        .param("lower", "0")
                        .param("higher", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].username").value("Alice"))
                .andExpect(jsonPath("$[0].email").isEmpty())
                .andExpect(jsonPath("$[0].roles[0]").value("USER"));

        verify(userSearchService).searchUsersById("1", 0, 50);
    }

    @Test
    void missingBoundsDefaultToFirstSearchWindow() throws Exception {
        when(userSearchService.searchUsersById("1", 0, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/search/id/{id}", "1"))
                .andExpect(status().isOk());

        verify(userSearchService).searchUsersById("1", 0, 50);
    }

    @Test
    void userProfileRequestReturnsPublicProfile() throws Exception {
        var profile = new ProfileDTO();
        profile.setId(1L);
        profile.setUsername("Alice");
        profile.setRoles(List.of("USER"));
        profile.setGamesPlayed(4);
        profile.setGamesWon(2);

        when(profileService.getPublicProfile(1L)).thenReturn(profile);

        mockMvc.perform(get("/api/users/{id}/profile", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("Alice"))
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.gamesPlayed").value(4))
                .andExpect(jsonPath("$.gamesWon").value(2));

        verify(profileService).getPublicProfile(1L);
    }

    @Test
    void partialBoundsReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/search/username/{username}", "Alice")
                        .param("lower", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("lower and higher must be provided together"));
    }

    @Test
    void invalidRangeReturnsBadRequest() throws Exception {
        when(userSearchService.searchUsersById("1", 0, 51))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search window cannot exceed 50 results"));

        mockMvc.perform(get("/api/users/search/id/{id}", "1")
                        .param("lower", "0")
                        .param("higher", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Search window cannot exceed 50 results"));
    }
}
