package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.LearningProfileRequest;
import com.kanjimastery.backend.dto.LearningProfileResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.LearningProfile;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.repository.LearningProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mục tiêu học của người học: cấp độ JLPT nhắm tới, ngày thi, thời gian ôn mỗi ngày, số từ mới tự chọn. */
@Service
@RequiredArgsConstructor
public class LearningProfileService {

    private final LearningProfileRepository profileRepository;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;
    private final FsrsParametersService fsrsParametersService;

    @Transactional(readOnly = true)
    public LearningProfileResponse get(Long userId) {
        return profileRepository.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> LearningProfileResponse.builder()
                        .configured(false)
                        .dailyMinutes(srsProperties.getDefaultDailyMinutes())
                        .scheduler(SchedulingSettings.DEFAULT.scheduler())
                        .desiredRetention(SchedulingSettings.DEFAULT.desiredRetention())
                        .fsrs(fsrsParametersService.status(userId))
                        .build());
    }

    /** Cách xếp lịch ôn của người học; chưa đặt mục tiêu thì SM-2 như trước. */
    @Transactional(readOnly = true)
    public SchedulingSettings scheduling(Long userId) {
        Fsrs fsrs = fsrsParametersService.fsrsFor(userId);
        return profileRepository.findById(userId)
                .map(profile -> new SchedulingSettings(profile.getScheduler(),
                        profile.getDesiredRetention().doubleValue(), fsrs))
                .orElseGet(() -> new SchedulingSettings(SchedulingSettings.DEFAULT.scheduler(),
                        SchedulingSettings.DEFAULT.desiredRetention(), fsrs));
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
        profile.setScheduler(request.getScheduler() != null ? request.getScheduler() : SchedulerType.SM2);
        profile.setDesiredRetention(request.getDesiredRetention() != null
                ? request.getDesiredRetention()
                : BigDecimal.valueOf(SchedulingSettings.DEFAULT.desiredRetention()));
        profile.setUpdatedAt(LocalDateTime.now());
        return toResponse(profileRepository.save(profile));
    }

    private LearningProfileResponse toResponse(LearningProfile profile) {
        return LearningProfileResponse.builder()
                .configured(true)
                .targetLevel(profile.getTargetLevel())
                .examDate(profile.getExamDate())
                .dailyMinutes(profile.getDailyMinutes())
                .newWordsPerDay(profile.getNewWordsPerDay())
                .scheduler(profile.getScheduler())
                .desiredRetention(profile.getDesiredRetention().doubleValue())
                .fsrs(fsrsParametersService.status(profile.getUserId()))
                .build();
    }
}
