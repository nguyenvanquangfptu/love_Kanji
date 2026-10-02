package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.LearningProfileRequest;
import com.kanjimastery.backend.dto.LearningProfileResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.LearningProfile;
import com.kanjimastery.backend.repository.LearningProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** Mục tiêu học của người học: cấp độ JLPT nhắm tới, ngày thi, thời gian ôn mỗi ngày, số từ mới tự chọn. */
@Service
@RequiredArgsConstructor
public class LearningProfileService {

    private final LearningProfileRepository profileRepository;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;

    @Transactional(readOnly = true)
    public LearningProfileResponse get(Long userId) {
        return profileRepository.findById(userId)
                .map(LearningProfileService::toResponse)
                .orElseGet(() -> LearningProfileResponse.builder()
                        .configured(false)
                        .dailyMinutes(srsProperties.getDefaultDailyMinutes())
                        .build());
    }

    @Transactional
    public LearningProfileResponse update(Long userId, LearningProfileRequest request) {
        if (request.getExamDate() != null && request.getExamDate().isBefore(calendar.today())) {
            throw new BadRequestException("Ngày thi đã qua - hãy chọn kỳ thi sắp tới");
        }
        LearningProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> LearningProfile.builder().userId(userId).build());
        profile.setTargetLevel(StringUtils.hasText(request.getTargetLevel()) ? request.getTargetLevel() : null);
        profile.setExamDate(request.getExamDate());
        profile.setDailyMinutes(request.getDailyMinutes());
        profile.setNewWordsPerDay(request.getNewWordsPerDay());
        profile.setUpdatedAt(LocalDateTime.now());
        return toResponse(profileRepository.save(profile));
    }

    private static LearningProfileResponse toResponse(LearningProfile profile) {
        return LearningProfileResponse.builder()
                .configured(true)
                .targetLevel(profile.getTargetLevel())
                .examDate(profile.getExamDate())
                .dailyMinutes(profile.getDailyMinutes())
                .newWordsPerDay(profile.getNewWordsPerDay())
                .build();
    }
}
