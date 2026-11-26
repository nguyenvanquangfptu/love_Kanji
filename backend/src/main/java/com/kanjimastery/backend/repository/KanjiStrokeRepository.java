package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.KanjiStroke;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KanjiStrokeRepository extends JpaRepository<KanjiStroke, String> {

    List<KanjiStroke> findAllByCharacterIn(Collection<String> characters);
}
