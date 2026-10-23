package com.kanjimastery.backend.controller;

import com.kanjimastery.backend.dto.SearchResultResponse;
import com.kanjimastery.backend.service.SearchService;
import com.kanjimastery.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Tìm từ", description = "Tìm từ trong kho bằng chữ Hán, kana, romaji, Hán Việt hoặc nghĩa")
public class SearchController {

    private final SearchService searchService;
    private final UserService userService;

    @Operation(summary = "Tìm từ",
            description = "Gõ chữ Hán, kana, romaji, Hán Việt hay nghĩa, có dấu hay không dấu. Tối đa 30 từ: khớp đúng cả từ "
                    + "trước, rồi khớp phần đầu, rồi chứa ở giữa; kèm tên bài và việc từ đã có trong lịch ôn chưa.")
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(Authentication authentication,
                                                             @RequestParam(defaultValue = "") String q) {
        Long userId = userService.getByUsername(authentication.getName()).getId();
        return ResponseEntity.ok(searchService.search(userId, q));
    }
}
