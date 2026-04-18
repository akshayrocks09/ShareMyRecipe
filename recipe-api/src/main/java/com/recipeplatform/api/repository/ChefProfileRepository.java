package com.recipeplatform.api.repository;

import com.recipeplatform.api.entity.ChefProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ChefProfileRepository extends JpaRepository<ChefProfile, UUID> {

    Optional<ChefProfile> findByHandle(String handle);

    Optional<ChefProfile> findByUserId(UUID userId);

    boolean existsByHandle(String handle);

    @Query("SELECT c.following FROM ChefProfile c WHERE c.id = :chefId")
    Set<ChefProfile> findFollowing(@Param("chefId") UUID chefId);

    @Query("SELECT c.followers FROM ChefProfile c WHERE c.id = :chefId")
    Set<ChefProfile> findFollowers(@Param("chefId") UUID chefId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
           "FROM ChefProfile c JOIN c.following f " +
           "WHERE c.id = :followerId AND f.id = :followeeId")
    boolean isFollowing(@Param("followerId") UUID followerId, @Param("followeeId") UUID followeeId);
}
