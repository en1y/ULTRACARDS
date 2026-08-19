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

    /**
     * The admin user report. `query` is the loose "any field" search the Users page has always
     * had; `username`, `email`, and `userId` are the per-field criteria the advanced search
     * fragment sends. Every supplied criterion is ANDed, so an admin can narrow on several at once.
     */
    @Query(value = """
            select distinct u from UserEntity u left join u.roles r
            where (:status is null or u.status = :status)
              and (:role is null or r = :role)
              and (:query is null
                   or (:exact = true and (lower(u.username) = lower(:query) or lower(u.email) = lower(:query)))
                   or (:exact = false and (lower(u.username) like lower(concat('%', :query, '%'))
                                            or lower(u.email) like lower(concat('%', :query, '%'))))
                   or cast(u.id as string) = :query)
              and (:username is null
                   or (:exact = true and lower(u.username) = lower(cast(:username as string)))
                   or (:exact = false and lower(u.username) like lower(concat('%', cast(:username as string), '%'))))
              and (:email is null
                   or (:exact = true and lower(u.email) = lower(cast(:email as string)))
                   or (:exact = false and lower(u.email) like lower(concat('%', cast(:email as string), '%'))))
              and (:userId is null or u.id = :userId)
            """, countQuery = """
            select count(distinct u.id) from UserEntity u left join u.roles r
            where (:status is null or u.status = :status)
              and (:role is null or r = :role)
              and (:query is null
                   or (:exact = true and (lower(u.username) = lower(:query) or lower(u.email) = lower(:query)))
                   or (:exact = false and (lower(u.username) like lower(concat('%', :query, '%'))
                                            or lower(u.email) like lower(concat('%', :query, '%'))))
                   or cast(u.id as string) = :query)
              and (:username is null
                   or (:exact = true and lower(u.username) = lower(cast(:username as string)))
                   or (:exact = false and lower(u.username) like lower(concat('%', cast(:username as string), '%'))))
              and (:email is null
                   or (:exact = true and lower(u.email) = lower(cast(:email as string)))
                   or (:exact = false and lower(u.email) like lower(concat('%', cast(:email as string), '%'))))
              and (:userId is null or u.id = :userId)
            """)
    Page<UserEntity> findAdminReport(@Param("status") com.ultracards.server.enums.UserStatus status,
                                     @Param("role") com.ultracards.server.enums.UserRole role,
                                     @Param("query") String query,
                                     @Param("exact") boolean exact,
                                     @Param("username") String username,
                                     @Param("email") String email,
                                     @Param("userId") Long userId,
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
