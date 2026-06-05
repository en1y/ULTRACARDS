package com.ultracards.server.repositories.friends;

import com.ultracards.server.entity.friends.FriendRelationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRelationRepository extends JpaRepository<FriendRelationEntity, UUID> {

    @EntityGraph(attributePaths = {"userOne", "userTwo"})
    @Query("""
            select relation
            from FriendRelationEntity relation
            where relation.userOne.id = :userId or relation.userTwo.id = :userId
            """)
    List<FriendRelationEntity> findByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"userOne", "userTwo"})
    @Query("""
            select relation
            from FriendRelationEntity relation
            where relation.userOne.id = :userOneId
                and relation.userTwo.id = :userTwoId
            """)
    Optional<FriendRelationEntity> findByNormalizedPair(
            @Param("userOneId") Long userOneId,
            @Param("userTwoId") Long userTwoId
    );
}
