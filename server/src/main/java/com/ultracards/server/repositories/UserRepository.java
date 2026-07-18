package com.ultracards.server.repositories;

import com.ultracards.server.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct u from UserEntity u join u.roles r where r = :role")
    List<UserEntity> findAllByRoleForUpdate(@Param("role") com.ultracards.server.enums.UserRole role);

    long countByStatus(com.ultracards.server.enums.UserStatus status);

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<UserEntity> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "roles")
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select distinct u from UserEntity u left join u.roles r
            where (:status is null or u.status = :status)
              and (:role is null or r = :role)
              and (:query is null
                   or (:exact = true and (lower(u.username) = lower(:query) or lower(u.email) = lower(:query)))
                   or (:exact = false and (lower(u.username) like lower(concat('%', :query, '%'))
                                            or lower(u.email) like lower(concat('%', :query, '%'))))
                   or cast(u.id as string) = :query)
            """, countQuery = """
            select count(distinct u.id) from UserEntity u left join u.roles r
            where (:status is null or u.status = :status)
              and (:role is null or r = :role)
              and (:query is null
                   or (:exact = true and (lower(u.username) = lower(:query) or lower(u.email) = lower(:query)))
                   or (:exact = false and (lower(u.username) like lower(concat('%', :query, '%'))
                                            or lower(u.email) like lower(concat('%', :query, '%'))))
                   or cast(u.id as string) = :query)
            """)
    Page<UserEntity> findAdminReport(@Param("status") com.ultracards.server.enums.UserStatus status,
                                     @Param("role") com.ultracards.server.enums.UserRole role,
                                     @Param("query") String query,
                                     @Param("exact") boolean exact,
                                     Pageable pageable);

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
