package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.TagRequest;
import com.kanjimastery.backend.dto.TagResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagResponse> list() {
        Map<Long, Long> counts = tagRepository.countKanjiPerTag().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return tagRepository.findAll(Sort.by("name")).stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .wordCount(counts.getOrDefault(tag.getId(), 0L))
                        .build())
                .toList();
    }

    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByName(request.getName())) {
            throw new BadRequestException("Tag '" + request.getName() + "' đã tồn tại");
        }
        Tag tag = new Tag();
        tag.setName(request.getName());
        return TagResponse.from(tagRepository.save(tag));
    }

    public TagResponse update(Long id, TagRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Tag với id: " + id));
        if (!tag.getName().equals(request.getName()) && tagRepository.existsByName(request.getName())) {
            throw new BadRequestException("Tag '" + request.getName() + "' đã tồn tại");
        }
        tag.setName(request.getName());
        return TagResponse.from(tagRepository.save(tag));
    }

    public void delete(Long id) {
        if (!tagRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy Tag với id: " + id);
        }
        tagRepository.deleteById(id);
    }
}
