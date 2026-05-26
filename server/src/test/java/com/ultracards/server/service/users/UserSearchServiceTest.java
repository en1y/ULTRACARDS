package com.ultracards.server.service.users;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSearchService userSearchService;

    @Test
    void rejectsSearchWindowLargerThanFiftyResults() {
        assertThatThrownBy(() -> userSearchService.searchUsersByUsername("Alice", 0, 51))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsInvalidRanges() {
        assertBadRange(-1, 10);
        assertBadRange(10, 10);
        assertBadRange(11, 10);

        verifyNoInteractions(userRepository);
    }

    @Test
    void sanitizesUsernameBeforeSearching() {
        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.searchByUsername(eq("Alice"), any(Pageable.class)))
                .thenReturn(List.of());

        userSearchService.searchUsersByUsername("<b>Alice</b>", 0, 10);

        verify(userRepository).searchByUsername(eq("Alice"), pageableCaptor.capture());
        var pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void passesStringIdBeforeSearching() {
        var pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.searchByIdPrefix(eq("1"), any(Pageable.class)))
                .thenReturn(List.of());

        userSearchService.searchUsersById("1", 0, 10);

        verify(userRepository).searchByIdPrefix(eq("1"), pageableCaptor.capture());
        var pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void mapsUsersToProfilesWithoutGameStats() {
        var user = new UserEntity("user@example.com", "ExampleUser");
        user.setId(1L);
        user.addRole(UserRole.USER);
        when(userRepository.searchByUsername(eq("Example"), any(Pageable.class)))
                .thenReturn(List.of(user));

        var results = userSearchService.searchUsersByUsername("Example", 0, 50);

        assertThat(results).hasSize(1);
        var profile = results.getFirst();
        assertThat(profile.getId()).isEqualTo(1L);
        assertThat(profile.getUsername()).isEqualTo("ExampleUser");
        assertThat(profile.getEmail()).isNull();
        assertThat(profile.getRoles()).containsExactly("USER");
        assertThat(profile.getGamesPlayed()).isNull();
        assertThat(profile.getGamesWon()).isNull();
        assertThat(profile.getUserGamesStats()).isNull();
    }

    private void assertBadRange(int lower, int higher) {
        assertThatThrownBy(() -> userSearchService.searchUsersById("1", lower, higher))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
