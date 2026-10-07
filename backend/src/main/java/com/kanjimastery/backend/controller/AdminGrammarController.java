package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.GrammarImportResult;
import com.kanjimastery.backend.dto.GrammarPointRequest;
import com.kanjimastery.backend.dto.GrammarPointResponse;
import com.kanjimastery.backend.service.GrammarPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/grammar-points")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Grammar", description = "Danh sách điểm ngữ pháp theo cấp độ (chỉ ADMIN)")
public class AdminGrammarController {

    private final GrammarPointService grammarPointService;

    @GetMapping
    public ResponseEntity<List<GrammarPointResponse>> list(@RequestParam(required = false) String level) {
        return ResponseEntity.ok(grammarPointService.list(level));
    }

    @PostMapping
    public ResponseEntity<GrammarPointResponse> create(@Valid @RequestBody GrammarPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grammarPointService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GrammarPointResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody GrammarPointRequest request) {
        return ResponseEntity.ok(grammarPointService.update(id, request));
    }

    @Operation(summary = "Xoá điểm ngữ pháp", description = "Câu thi đã gắn với điểm này vẫn giữ, chỉ mất liên kết.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        grammarPointService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Nhập danh sách từ CSV",
            description = "Body {\"csv\": \"...\"}. Cột: cấp độ, bài, mẫu, nghĩa tiếng Việt, cách nối, giải thích (hai "
                    + "cột cuối có thể bỏ); dòng đầu là tiêu đề thì bỏ qua. Cùng cấp độ + mẫu thì cập nhật, không "
                    + "tạo trùng; dòng lỗi được bỏ qua và báo lại.")
    @PostMapping("/import")
    public ResponseEntity<GrammarImportResult> importCsv(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(grammarPointService.importCsv(body.get("csv")));
    }

    @Operation(summary = "Xuất danh sách ra CSV", description = "Cùng định dạng với lúc nhập.")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String level) {
        String fileName = "ngu-phap" + (level == null ? "" : "-" + level.toLowerCase()) + ".csv";
        // BOM để Excel đọc đúng tiếng Việt, tiếng Nhật.
        byte[] csv = ("\uFEFF" + grammarPointService.exportCsv(level)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
