package com.ultracards.server.controllers.users;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.server.service.users.ProfileService;
import com.ultracards.server.service.users.UserSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserSearchService userSearchService;
    private final ProfileService profileService;

    @GetMapping("/search/username/{username}")
    public List<ProfileDTO> searchUsersByUsername(
            @PathVariable String username,
            @RequestParam(required = false) Integer lower,
            @RequestParam(required = false) Integer higher
    ) {
        var bounds = resolveBounds(lower, higher);
        return userSearchService.searchUsersByUsername(username, bounds.lower(), bounds.higher());
    }

    @GetMapping("/search/id/{id}")
    public List<ProfileDTO> searchUsersById(
            @PathVariable String id,
            @RequestParam(required = false) Integer lower,
            @RequestParam(required = false) Integer higher
    ) {
        var bounds = resolveBounds(lower, higher);
        return userSearchService.searchUsersById(id, bounds.lower(), bounds.higher());
    }

    @GetMapping("/{id}/profile")
    public ProfileDTO getUserProfile(
            @PathVariable Long id
    ) {
        return profileService.getPublicProfile(id);
    }

    private SearchBounds resolveBounds(Integer lower, Integer higher) {
        if (lower == null && higher == null)
            return new SearchBounds(0, UserSearchService.MAX_RESULTS);

        if (lower == null || higher == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lower and higher must be provided together");

        return new SearchBounds(lower, higher);
    }

    private record SearchBounds(int lower, int higher) {}
}
