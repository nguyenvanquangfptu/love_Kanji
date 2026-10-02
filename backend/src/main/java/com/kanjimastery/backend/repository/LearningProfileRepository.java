package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.LearningProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProfileRepository extends JpaRepository<LearningProfile, Long> {
}
