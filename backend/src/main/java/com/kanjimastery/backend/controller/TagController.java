package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.TagRequest;
import com.kanjimastery.backend.dto.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.kanjimastery.backend.service.TagService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Quản lý tag để nhóm/lọc Kanji (chỉ dùng trong Admin)")
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagService.list());
    }

    @Operation(summary = "Tạo tag mới (chỉ ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.create(request));
    }

    @Operation(summary = "Sửa tên tag (chỉ ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return ResponseEntity.ok(tagService.update(id, request));
    }

    @Operation(summary = "Xoá tag (chỉ ADMIN) - Kanji liên quan chỉ mất tag này, không bị xoá")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
