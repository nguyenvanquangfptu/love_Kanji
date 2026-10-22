package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.CharacterDetailResponse;
import com.kanjimastery.backend.dto.WordCharacterResponse;
import com.kanjimastery.backend.service.UserService;
import com.kanjimastery.backend.service.WordCharacterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/characters")
@RequiredArgsConstructor
@Tag(name = "Chữ Hán", description = "Hán Việt từng chữ và các từ cùng chữ")
public class CharacterController {

    private final WordCharacterService wordCharacterService;
    private final UserService userService;

    @Operation(summary = "Các chữ Hán của nhiều từ",
            description = "Theo id từ: các chữ Hán theo thứ tự trong từ, kèm âm Hán Việt (勉強: 勉 MIỄN, 強 CƯỜNG). Từ không "
                    + "tách được thì không có trong kết quả. Tối đa 200 từ mỗi lần.")
    @GetMapping
    public ResponseEntity<Map<Long, List<WordCharacterResponse>>> parts(@RequestParam List<Long> wordIds) {
        return ResponseEntity.ok(wordCharacterService.partsOf(wordIds));
    }

    @Operation(summary = "Một chữ Hán và các từ chứa nó",
            description = "Các từ trong kho có chữ này, gom theo âm Hán Việt của chữ trong từng từ, kèm việc từ đã có trong "
                    + "lịch ôn của người học chưa.")
    @GetMapping("/{character}")
    public ResponseEntity<CharacterDetailResponse> detail(Authentication authentication,
                                                          @PathVariable String character) {
        Long userId = userService.getByUsername(authentication.getName()).getId();
        return ResponseEntity.ok(wordCharacterService.detail(userId, character));
    }
}
