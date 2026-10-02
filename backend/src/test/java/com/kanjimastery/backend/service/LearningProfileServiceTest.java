package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.LearningProfileRequest;
import com.kanjimastery.backend.dto.LearningProfileResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.LearningProfile;
import com.kanjimastery.backend.model.SchedulerType;
import com.kanjimastery.backend.repository.LearningProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningProfileServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private LearningProfileRepository profileRepository;

    private LearningProfileService service;

    @BeforeEach
    void setUp() {
        SrsProperties properties = new SrsProperties();
        // 10:00 UTC ngày 02/10 = ngày học 02/10 ở Việt Nam.
        StudyCalendar calendar = new StudyCalendar(properties, Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC));
        service = new LearningProfileService(profileRepository, properties, calendar);
    }

    @Test
    void get_shouldReturnTheDefaults_whenNoGoalWasSet() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        LearningProfileResponse profile = service.get(USER_ID);

        assertThat(profile.isConfigured()).isFalse();
        assertThat(profile.getDailyMinutes()).isEqualTo(20);
        assertThat(profile.getTargetLevel()).isNull();
        assertThat(profile.getScheduler()).isEqualTo(SchedulerType.SM2);
        assertThat(profile.getDesiredRetention()).isEqualTo(0.9);
    }

    @Test
    void scheduling_shouldKeepSm2_untilTheLearnerChoosesFsrs() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThat(service.scheduling(USER_ID)).isEqualTo(SchedulingSettings.DEFAULT);

        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(LearningProfile.builder().userId(USER_ID)
                .dailyMinutes(20).scheduler(SchedulerType.FSRS).desiredRetention(new BigDecimal("0.85")).build()));
        SchedulingSettings settings = service.scheduling(USER_ID);
        assertThat(settings.usesFsrs()).isTrue();
        assertThat(settings.desiredRetention()).isEqualTo(0.85);
    }

    @Test
    void update_shouldSaveTheChosenScheduler_andFallBackToSm2At90Percent() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.save(any(LearningProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningProfileRequest fsrs = request("N4", null, 20, null);
        fsrs.setScheduler(SchedulerType.FSRS);
        fsrs.setDesiredRetention(new BigDecimal("0.85"));
        LearningProfileResponse chosen = service.update(USER_ID, fsrs);
        assertThat(chosen.getScheduler()).isEqualTo(SchedulerType.FSRS);
        assertThat(chosen.getDesiredRetention()).isEqualTo(0.85);

        LearningProfileResponse unset = service.update(USER_ID, request("N4", null, 20, null));
        assertThat(unset.getScheduler()).isEqualTo(SchedulerType.SM2);
        assertThat(unset.getDesiredRetention()).isEqualTo(0.9);
    }

    @Test
    void update_shouldCreateTheGoal_andTreatABlankLevelAsNone() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.save(any(LearningProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningProfileResponse profile = service.update(USER_ID, request("", LocalDate.of(2026, 12, 6), 30, null));

        assertThat(profile.isConfigured()).isTrue();
        assertThat(profile.getTargetLevel()).isNull();
        assertThat(profile.getExamDate()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(profile.getDailyMinutes()).isEqualTo(30);
    }

    @Test
    void update_shouldAcceptAnExamToday_butNotOneThatIsOver() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.save(any(LearningProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(USER_ID, request("N4", LocalDate.of(2026, 10, 2), 20, 15)).getExamDate())
                .isEqualTo(LocalDate.of(2026, 10, 2));
        assertThatThrownBy(() -> service.update(USER_ID, request("N4", LocalDate.of(2026, 10, 1), 20, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void update_shouldNotSave_whenTheExamIsOver() {
        assertThatThrownBy(() -> service.update(USER_ID, request("N4", LocalDate.of(2025, 12, 7), 20, null)))
                .isInstanceOf(BadRequestException.class);
        verify(profileRepository, never()).save(any());
    }

    private static LearningProfileRequest request(String level, LocalDate examDate, int minutes, Integer newWords) {
        LearningProfileRequest request = new LearningProfileRequest();
        request.setTargetLevel(level);
        request.setExamDate(examDate);
        request.setDailyMinutes(minutes);
        request.setNewWordsPerDay(newWords);
        return request;
    }
}
