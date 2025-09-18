package com.ultracards.server.entity;

import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.enums.games.GameType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant userCreatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login")
    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_roles_user")),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_user_roles_user_role",
                    columnNames = {"user_id", "role"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Set<UserRole> roles = new HashSet<>();

    /* -------- Basic entity constructor -------- */

    public UserEntity(String email, String username) {
        this.email = email;
        this.username = username;
        enabled = true;
    }

    /* -------- convenience API -------- */

    public boolean addRole(UserRole role) { return roles.add(role); }
    public boolean removeRole(UserRole role) { return roles.remove(role); }
    public boolean hasRole(UserRole role) { return roles.contains(role); }

    /* ----------- UserDetails ----------- */

    @Override
    public Collection<UserRole> getAuthorities() {
        return getRoles();
    }

    @Override
    public String getPassword() {
        // My app has passwordless auth so I return null
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return getStatus().equals(UserStatus.ACTIVE);
    }

    @Override
    public boolean isAccountNonLocked() {
        return !getStatus().equals(UserStatus.DISABLED);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !getStatus().equals(UserStatus.DISABLED);
    }

    /* ------------- toString ------------- */

    @Override
    public String toString() {
        return String.format("%s: [%s]", getUsername(),  getRoles().toString());
    }
    /* ----------- equals/hashCode ---------- */

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserEntity that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(username, that.username);
    }
    @Override public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(username);
    }
}
