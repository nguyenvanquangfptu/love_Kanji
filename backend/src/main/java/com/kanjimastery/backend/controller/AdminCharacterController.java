package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.CharacterRebuildReport;
import com.kanjimastery.backend.service.WordCharacterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Operation(summary = "Tách lại Hán Việt từng chữ cho cả kho từ",
            description = "Chạy sau khi nhập từ vựng thẳng vào DB. Mặc định chỉ thử (dryRun=true) và trả báo cáo các từ "
                    + "không tách được; dryRun=false mới ghi.")
    @PostMapping("/rebuild")
    public ResponseEntity<CharacterRebuildReport> rebuild(@RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(wordCharacterService.rebuild(dryRun));
    }
}
