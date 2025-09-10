package com.ultracards.server.entity;

import com.ultracards.server.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "user_roles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role"}))
public class UserRole {
    @Id
    @GeneratedValue
    @Getter
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @Setter
    @Getter
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Setter
    @Getter
    private Role role;

    public UserRole() {}

    public UserRole(UserEntity user, Role role) {
        this.user = user;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

