package com.ultracards.server.entity;

import com.ultracards.server.enums.Role;
import com.ultracards.server.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant userCreatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login")
    private Instant lastLoginAt;

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private Set<UserRole> roles = new HashSet<>();

    public UserEntity(String email, String username) {
        this.email = email;
        this.username = username;
        enabled = true;
    }

    public void addRole(Role role) {
        roles.add(new UserRole(this, role));
    }

    public void removeRole(Role role) {
        roles.removeIf(userRole -> userRole.getRole().equals(role));
    }

    public void setRoles(Set<Role> roles) {
        this.roles.clear();
        if (roles != null) {
            roles.forEach(role -> {
                this.roles.add(new UserRole(
                        this, role
                ));
            });
        }
    }

    @Override
    public String toString() {
        return getUsername();
    }
}
