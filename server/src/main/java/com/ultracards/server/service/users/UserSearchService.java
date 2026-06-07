package com.ultracards.server.service.users;

import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSearchService {

    public static final int MAX_RESULTS = 50;
    private static final PolicyFactory NO_HTML_POLICY = new HtmlPolicyBuilder().toFactory();

    private final UserRepository userRepository;

    public List<ProfileDTO> searchUsersByUsername(String username, int lower, int higher) {
        var pageable = createPageable(lower, higher);
        var sanitizedUsername = NO_HTML_POLICY.sanitize(username);
        var users = userRepository.searchByUsername(sanitizedUsername, pageable);

        return toProfileDTOs(users);
    }

    public List<ProfileDTO> searchUsersById(String id, int lower, int higher) {
        var pageable = createPageable(lower, higher);
        var users = userRepository.searchByIdPrefix(id, pageable);

        return toProfileDTOs(users);
    }

    private PageRequest createPageable(int lower, int higher) {
        if (lower < 0)
            throw badRequest("lower must be greater than or equal to 0");

        if (higher <= lower)
            throw badRequest("higher must be greater than lower");

        var pageSize = higher - lower;

        if (pageSize > MAX_RESULTS)
            throw badRequest("Search window cannot exceed " + MAX_RESULTS + " results");

        return PageRequest.of(
                lower / pageSize,
                pageSize,
                Sort.by(Sort.Direction.ASC, "id")
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private List<ProfileDTO> toProfileDTOs(List<UserEntity> users) {
        var profiles = new ArrayList<ProfileDTO>();
        for (var user : users) {
            profiles.add(toProfileDTO(user));
        }
        return profiles;
    }

    private ProfileDTO toProfileDTO(UserEntity user) {
        var profile = new ProfileDTO();
        var roles = new ArrayList<String>();

        for (var role : user.getRoles())
            roles.add(role.toString());

        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setRoles(roles);
        return profile;
    }
}
