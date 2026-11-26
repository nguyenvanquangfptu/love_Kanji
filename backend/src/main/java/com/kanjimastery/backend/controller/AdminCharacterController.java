package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.dto.HanVietRequest;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.dto.VocabularyQualityReport;
import com.kanjimastery.backend.service.KanjiService;
import com.kanjimastery.backend.service.VocabularyQualityService;
import com.kanjimastery.backend.service.WordCharacterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/characters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Chữ Hán", description = "Tách Hán Việt từng chữ cho kho từ vựng")
public class AdminCharacterController {

    private final WordCharacterService wordCharacterService;
    private final VocabularyQualityService vocabularyQualityService;
    private final KanjiService kanjiService;

    @Operation(summary = "Tách lại Hán Việt từng chữ cho cả kho từ",
            description = "Chạy sau khi nhập từ vựng thẳng vào DB. Mặc định chỉ thử (dryRun=true) và trả báo cáo các từ "
                    + "không tách được; dryRun=false mới ghi.")
    @PostMapping("/rebuild")
    public ResponseEntity<CharacterRebuildReport> rebuild(@RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(wordCharacterService.rebuild(dryRun));
    }

    @Operation(summary = "Báo cáo chất lượng kho từ",
            description = "Từ có số nét lệch với bảng số nét từng chữ, chữ Hán được ghi nhiều âm Hán Việt, từ chưa có Hán Việt.")
    @GetMapping("/quality")
    public ResponseEntity<VocabularyQualityReport> quality() {
        return ResponseEntity.ok(vocabularyQualityService.report());
    }

    @Operation(summary = "Sửa số nét của các từ theo bảng số nét từng chữ",
            description = "Mặc định chỉ thử (dryRun=true) và trả số từ sẽ sửa; dryRun=false mới ghi.")
    @PostMapping("/stroke-counts/fix")
    public ResponseEntity<VocabularyQualityReport.StrokeFix> fixStrokeCounts(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(vocabularyQualityService.fixStrokeCounts(dryRun));
    }

    @Operation(summary = "Sửa âm Hán Việt của một từ", description = "Tách lại từng chữ ngay; không tách được thì báo lỗi 400.")
    @PutMapping("/words/{id}/han-viet")
    public ResponseEntity<KanjiResponse> updateHanViet(@PathVariable Long id, @Valid @RequestBody HanVietRequest request) {
        return ResponseEntity.ok(kanjiService.updateHanViet(id, request.hanViet()));
    }
}
