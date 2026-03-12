package com.ultracards.server.repositories;

import com.ultracards.server.entity.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findById(Long id);
}
