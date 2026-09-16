package com.theotech.iam.repository;

import com.theotech.iam.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Active (non-deleted) user with roles loaded in one go — used at login. */
    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("select u from User u where lower(u.username) = lower(:username) and u.deletedAt is null")
    Optional<User> findActiveByUsernameWithAuthorities(@Param("username") String username);

    @Query("select u from User u where lower(u.username) = lower(:username) and u.deletedAt is null")
    Optional<User> findActiveByUsername(@Param("username") String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and u.deletedAt is null")
    boolean existsActiveByUsername(@Param("username") String username);

    /** Workers = active users holding the worker role. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u join u.roles r where r.name = :role and u.deletedAt is null order by u.fullName")
    List<User> findActiveByRole(@Param("role") String role);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") Long id);
}
