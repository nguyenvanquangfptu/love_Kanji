package com.kanjimastery.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.kanjimastery.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
