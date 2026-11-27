package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.KanjiRequest;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.dto.MnemonicResponse;
import com.kanjimastery.backend.dto.VocabularyImportRequest;
import com.kanjimastery.backend.dto.VocabularyImportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.kanjimastery.backend.service.KanjiService;
import com.kanjimastery.backend.service.MnemonicService;
import com.kanjimastery.backend.service.VocabularyImportService;
import com.kanjimastery.backend.service.WordSentenceService;
import com.kanjimastery.backend.dto.WordSentenceResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kanji")
@RequiredArgsConstructor
@Tag(name = "Kanji Dictionary", description = "Tra cứu từ điển Kanji (có Redis cache)")
public class KanjiController {

    private final KanjiService kanjiService;
    private final MnemonicService mnemonicService;
    private final VocabularyImportService vocabularyImportService;
    private final WordSentenceService wordSentenceService;

    @GetMapping
    public ResponseEntity<Page<KanjiResponse>> search(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(kanjiService.search(level, keyword, tagId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KanjiResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(kanjiService.getById(id));
    }

    @Operation(summary = "Câu dùng từ này (tối đa 3)",
            description = "Câu ví dụ của từ trước, rồi câu trong ngân hàng đề đã duyệt có gắn từ đó; kèm furigana và "
                    + "đánh dấu chỗ có từ (kể cả khi từ đã chia).")
    @GetMapping("/{id}/sentences")
    public ResponseEntity<List<WordSentenceResponse>> sentences(@PathVariable Long id) {
        return ResponseEntity.ok(wordSentenceService.sentences(id));
    }

    @Operation(summary = "Tạo Kanji mới (chỉ ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<KanjiResponse> create(@Valid @RequestBody KanjiRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kanjiService.create(request));
    }

    @Operation(summary = "Sửa Kanji (chỉ ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<KanjiResponse> update(@PathVariable Long id, @Valid @RequestBody KanjiRequest request) {
        return ResponseEntity.ok(kanjiService.update(id, request));
    }

    @Operation(summary = "Xoá Kanji (chỉ ADMIN) - xoá cascade luôn tiến độ SRS liên quan của mọi user")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        kanjiService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mẹo nhớ của một từ",
            description = "Có sẵn thì trả luôn; chưa có thì nhờ AI (Gemini) sinh dựa trên âm Hán Việt rồi lưu lại "
                    + "cho mọi người dùng chung. Chưa cấu hình GEMINI_API_KEY thì trả 400.")
    @PostMapping("/{id}/mnemonic")
    public ResponseEntity<MnemonicResponse> mnemonic(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(mnemonicService.getOrGenerate(authentication.getName(), id));
    }

    @Operation(summary = "Nhập từ vựng của một bài từ file JSON (chỉ ADMIN)",
            description = "Mặc định chỉ kiểm tra (dryRun=true) và trả số từ mới, số từ gắn thêm, lỗi và cảnh báo; "
                    + "dryRun=false mới ghi. Có lỗi thì không ghi gì; nhập lại cùng file không tạo trùng.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import")
    public ResponseEntity<VocabularyImportResponse> importLesson(@RequestBody VocabularyImportRequest request,
                                                                 @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(vocabularyImportService.importLesson(request, dryRun));
    }
}
