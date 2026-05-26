package com.ultracards.server.repositories;

import com.ultracards.server.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findById(Long id);

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select u
            from UserEntity u
            where :username <> ''
                and lower(u.username) like lower(concat('%', :username, '%'))
            """)
    List<UserEntity> searchByUsername(
            @Param("username") String username,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select u
            from UserEntity u
            where cast(u.id as string) like concat(:id, '%')
            """)
    List<UserEntity> searchByIdPrefix(
            @Param("id") String id,
            Pageable pageable
    );
}
