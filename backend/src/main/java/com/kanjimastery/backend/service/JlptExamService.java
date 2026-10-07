package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.ExamMondaiResponse;
import com.kanjimastery.backend.dto.ExamSittingResponse;
import com.kanjimastery.backend.dto.JlptLevelResponse;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.ExamSitting;
import com.kanjimastery.backend.model.ExamSittingStatus;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSittingRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Làm đề JLPT theo cấu trúc đề thật: một buổi thi ({@link ExamSitting}) gồm các phần đã chọn (Từ vựng, Ngữ pháp), mỗi
 * phần là một lượt thi có giờ riêng - đếm giờ, tự nộp, chấm điểm, xem lại dùng chung với thi nhanh. Phần sau chỉ bắt
 * đầu khi phần trước đã nộp hoặc hết giờ, và không quay lại phần trước được, như đề thật.
 */
@Service
@RequiredArgsConstructor
public class JlptExamService {

    /** Thang điểm ước tính của buổi thi. */
    static final int ESTIMATED_SCALE = 60;

    /**
     * Một buổi thi vừa hoàn thành với đủ các phần của cấp độ - để đưa lên bảng xếp hạng đề JLPT.
     *
     * @param correct số câu đúng trên {@code total} câu của các phần
     * @param seconds tổng thời gian làm các phần (không tính phần chốt trễ quá giờ)
     */
    public record CompletedSitting(Long userId, JlptLevel level, int correct, int total, int seconds) {
    }

    private final JlptBlueprintProperties blueprints;
    private final JlptExamAssembler assembler;
    private final ExamQuestionRepository questionRepository;
    private final ExamSittingRepository sittingRepository;
    private final UserExamAttemptRepository attemptRepository;
    private final UserExamAnswerRepository answerRepository;
    private final ExamService examService;
    private final Clock clock;

    /** Các cấp độ có cấu trúc đề, kèm số câu đã duyệt hiện có của từng dạng. */
    @Transactional(readOnly = true)
    public List<JlptLevelResponse> levels() {
        return blueprints.getLevels().entrySet().stream()
                .map(entry -> levelResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private JlptLevelResponse levelResponse(JlptLevel level, JlptBlueprintProperties.Level blueprint) {
        Map<JlptQuestionType, Long> available = availableByType(level);
        List<JlptLevelResponse.Section> sections = blueprint.getSections().stream()
                .map(section -> {
                    List<JlptLevelResponse.Mondai> mondai = new ArrayList<>();
                    int questionCount = 0;
                    for (Map.Entry<JlptQuestionType, Integer> entry : section.getQuestions().entrySet()) {
                        int have = available.getOrDefault(entry.getKey(), 0L).intValue();
                        mondai.add(new JlptLevelResponse.Mondai(mondai.size() + 1, entry.getKey(), entry.getValue(),
                                have));
                        questionCount += Math.min(have, entry.getValue());
                    }
                    return JlptLevelResponse.Section.builder()
                            .name(section.getName())
                            .plannedQuestions(section.plannedQuestions())
                            .plannedMinutes(section.getMinutes())
                            .questionCount(questionCount)
                            .minutes(durationSeconds(section, questionCount) / 60)
                            .mondai(mondai)
                            .build();
                })
                .toList();
        return new JlptLevelResponse(level, sections);
    }

    /** Tạo buổi thi với các phần đã chọn và bắt đầu ngay phần đầu tiên. */
    @Transactional
    public StartExamResponse startSitting(Long userId, StartJlptExamRequest request) {
        JlptLevel level = Levels.require(request.getJlptLevel());
        JlptBlueprintProperties.Level blueprint = blueprints.getLevels().get(level);
        if (blueprint == null) {
            throw new BadRequestException("Chưa có cấu trúc đề JLPT cho cấp độ: " + level);
        }
        Set<ExamSection> chosen = new HashSet<>();
        for (String name : request.getSections()) {
            try {
                chosen.add(ExamSection.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Phần thi không hợp lệ: " + request.getSections());
            }
        }
        List<ExamSection> sections = blueprint.getSections().stream()
                .map(JlptBlueprintProperties.Section::getName)
                .filter(chosen::contains)
                .toList();
        if (sections.size() != chosen.size()) {
            throw new BadRequestException("Phần thi không hợp lệ: " + request.getSections());
        }
        // Phần nào chưa có câu thì báo ngay, không để người học làm xong phần đầu mới biết phần sau không có đề.
        Map<JlptQuestionType, Long> available = availableByType(level);
        for (JlptBlueprintProperties.Section section : blueprint.getSections()) {
            if (sections.contains(section.getName())
                    && section.types().stream().noneMatch(type -> available.getOrDefault(type, 0L) > 0)) {
                throw new BadRequestException(
                        "Phần " + section.getName().vietnameseName() + " của đề " + level + " chưa có câu hỏi");
            }
        }

        ExamSitting sitting = sittingRepository.save(ExamSitting.builder()
                .userId(userId)
                .jlptLevel(level)
                .sections(sections.stream().map(ExamSection::name).collect(Collectors.joining(",")))
                .startedAt(LocalDateTime.now(clock))
                .build());
        return startSection(sitting, sections.get(0), new HashSet<>());
    }

    /** Bắt đầu phần kế tiếp của buổi thi, sau khi phần trước đã nộp hoặc hết giờ. */
    @Transactional
    public StartExamResponse startNextSection(Long userId, Long sittingId) {
        ExamSitting sitting = sittingRepository.findByIdForUpdate(sittingId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi thi: " + sittingId));
        if (!ExamSittingStatus.IN_PROGRESS.equals(sitting.getStatus())) {
            throw new BadRequestException("Buổi thi đã kết thúc");
        }
        List<UserExamAttempt> attempts = attemptRepository.findBySittingIdOrderByIdAsc(sittingId);
        if (attempts.stream().anyMatch(JlptExamService::inProgress)) {
            throw new BadRequestException("Phần thi đang làm chưa kết thúc");
        }
        ExamSection next = nextSection(sitting, attempts);
        if (next == null) {
            throw new BadRequestException("Đã làm hết các phần của buổi thi");
        }
        return startSection(sitting, next, wordsAsked(attempts));
    }

    private StartExamResponse startSection(ExamSitting sitting, ExamSection sectionName, Set<Long> wordsAsked) {
        JlptBlueprintProperties.Section section = blueprints.section(sitting.getJlptLevel(), sectionName)
                .orElseThrow(() -> new BadRequestException("Không còn cấu trúc đề cho phần thi: " + sectionName));
        List<JlptExamAssembler.Mondai> mondai = assembler.assemble(sitting.getUserId(), sitting.getJlptLevel(), section, wordsAsked).stream()
                .filter(part -> !part.questions().isEmpty())
                .toList();
        List<ExamQuestion> questions = mondai.stream().flatMap(part -> part.questions().stream()).toList();
        if (questions.isEmpty()) {
            throw new BadRequestException("Phần " + sectionName.vietnameseName() + " của đề "
                    + sitting.getJlptLevel() + " chưa có câu hỏi");
        }

        UserExamAttempt attempt = UserExamAttempt.builder()
                .userId(sitting.getUserId())
                .jlptLevel(sitting.getJlptLevel())
                .sittingId(sitting.getId())
                .section(sectionName)
                .durationSeconds(durationSeconds(section, questions.size()))
                .build();
        return examService.begin(attempt, questions, mondai.stream()
                .map(part -> new ExamMondaiResponse(part.number(), part.type(), part.questions().size(),
                        part.plannedCount()))
                .toList());
    }

    /** Từ đã hỏi ở các phần đã làm của buổi thi. */
    private Set<Long> wordsAsked(List<UserExamAttempt> attempts) {
        List<Long> questionIds = attempts.stream()
                .flatMap(attempt -> answerRepository.findByAttemptIdOrderByIdAsc(attempt.getId()).stream())
                .map(UserExamAnswer::getQuestionId)
                .toList();
        if (questionIds.isEmpty()) {
            return new HashSet<>();
        }
        return questionRepository.findAllWithWordsByIdIn(questionIds).stream()
                .flatMap(question -> question.getKanjiIds().stream())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Transactional(readOnly = true)
    public ExamSittingResponse getSitting(Long userId, Long sittingId) {
        ExamSitting sitting = sittingRepository.findById(sittingId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi thi: " + sittingId));
        List<UserExamAttempt> attempts = attemptRepository.findBySittingIdOrderByIdAsc(sittingId);
        Map<ExamSection, UserExamAttempt> bySection = attempts.stream()
                .collect(Collectors.toMap(UserExamAttempt::getSection, Function.identity()));

        List<ExamSittingResponse.Section> sections = sitting.sectionList().stream()
                .map(name -> {
                    UserExamAttempt attempt = bySection.get(name);
                    if (attempt == null) {
                        return ExamSittingResponse.Section.builder().name(name).build();
                    }
                    return ExamSittingResponse.Section.builder()
                            .name(name)
                            .attemptId(attempt.getId())
                            .status(attempt.getStatus())
                            .totalScore(attempt.getTotalScore())
                            .totalQuestions(inProgress(attempt)
                                    ? null
                                    : (int) answerRepository.countByAttemptId(attempt.getId()))
                            .timeSpentSeconds(attempt.getTimeSpentSeconds())
                            .durationSeconds(attempt.getDurationSeconds())
                            .build();
                })
                .toList();
        boolean canContinue = ExamSittingStatus.IN_PROGRESS.equals(sitting.getStatus())
                && attempts.stream().noneMatch(JlptExamService::inProgress);
        int correct = 0;
        int total = 0;
        for (ExamSittingResponse.Section section : sections) {
            if (section.getTotalQuestions() != null) {
                correct += section.getTotalScore() == null ? 0 : section.getTotalScore();
                total += section.getTotalQuestions();
            }
        }
        return ExamSittingResponse.builder()
                .sittingId(sitting.getId())
                .jlptLevel(sitting.getJlptLevel())
                .status(sitting.getStatus())
                .startedAt(sitting.getStartedAt())
                .finishedAt(sitting.getFinishedAt())
                .sections(sections)
                .nextSection(canContinue ? nextSection(sitting, attempts) : null)
                .estimatedScore(total > 0 ? estimatedScore(correct, total) : null)
                .build();
    }

    /**
     * Một phần thi vừa chốt điểm (nộp bài, hết giờ): làm xong mọi phần thì buổi thi hoàn thành. Gọi sau khi transaction
     * chốt điểm đã commit nên chạy trong transaction riêng.
     *
     * @return kết quả buổi thi khi lần gọi này hoàn thành một buổi thi đủ các phần của cấp độ
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CompletedSitting> onSectionFinished(Long sittingId) {
        ExamSitting sitting = sittingRepository.findById(sittingId).orElse(null);
        if (sitting == null || !ExamSittingStatus.IN_PROGRESS.equals(sitting.getStatus())) {
            return Optional.empty();
        }
        List<UserExamAttempt> attempts = attemptRepository.findBySittingIdOrderByIdAsc(sittingId);
        if (allSectionsFinished(sitting, attempts)
                && sittingRepository.finishIfInProgress(sittingId, ExamSittingStatus.COMPLETED, LocalDateTime.now(clock)) > 0) {
            return completedSitting(sitting, attempts);
        }
        return Optional.empty();
    }

    /**
     * Kết thúc buổi thi đã quá lâu (job dọn dẹp): hoàn thành nếu thực ra đã làm hết các phần, không thì bỏ dở - kết quả
     * các phần đã làm vẫn giữ. Còn phần đang làm dở thì để lượt quét sau.
     */
    @Transactional
    public Optional<CompletedSitting> closeStale(Long sittingId) {
        ExamSitting sitting = sittingRepository.findByIdForUpdate(sittingId).orElse(null);
        if (sitting == null || !ExamSittingStatus.IN_PROGRESS.equals(sitting.getStatus())) {
            return Optional.empty();
        }
        List<UserExamAttempt> attempts = attemptRepository.findBySittingIdOrderByIdAsc(sittingId);
        if (attempts.stream().anyMatch(JlptExamService::inProgress)) {
            return Optional.empty();
        }
        boolean finished = allSectionsFinished(sitting, attempts);
        int updated = sittingRepository.finishIfInProgress(sittingId,
                finished ? ExamSittingStatus.COMPLETED : ExamSittingStatus.ABANDONED, LocalDateTime.now(clock));
        return finished && updated > 0 ? completedSitting(sitting, attempts) : Optional.empty();
    }

    /** Kết quả cả buổi thi, chỉ khi buổi thi gồm mọi phần trong cấu trúc đề của cấp độ (đề trọn vẹn). */
    private Optional<CompletedSitting> completedSitting(ExamSitting sitting, List<UserExamAttempt> attempts) {
        JlptBlueprintProperties.Level blueprint = blueprints.getLevels().get(sitting.getJlptLevel());
        List<ExamSection> allSections = blueprint == null
                ? List.of()
                : blueprint.getSections().stream().map(JlptBlueprintProperties.Section::getName).toList();
        if (allSections.isEmpty() || !sitting.sectionList().containsAll(allSections)) {
            return Optional.empty();
        }
        int correct = 0;
        int total = 0;
        int seconds = 0;
        for (UserExamAttempt attempt : attempts) {
            correct += attempt.getTotalScore() == null ? 0 : attempt.getTotalScore();
            total += (int) answerRepository.countByAttemptId(attempt.getId());
            int spent = attempt.getTimeSpentSeconds() == null ? 0 : attempt.getTimeSpentSeconds();
            seconds += attempt.getDurationSeconds() == null ? spent : Math.min(spent, attempt.getDurationSeconds());
        }
        return total > 0
                ? Optional.of(new CompletedSitting(sitting.getUserId(), sitting.getJlptLevel(), correct, total, seconds))
                : Optional.empty();
    }

    /** Điểm ước tính thang 0-60 (thang của môn Kiến thức ngôn ngữ N3): tỉ lệ đúng × 60, làm tròn. */
    static int estimatedScore(int correct, int total) {
        return (int) Math.round(correct * (double) ESTIMATED_SCALE / total);
    }

    /**
     * Thời gian làm bài theo đề thật; đề ít câu hơn đề thật (ngân hàng câu chưa đủ) thì giảm theo tỉ lệ số câu, làm
     * tròn tới phút, ít nhất 1 phút.
     */
    static int durationSeconds(JlptBlueprintProperties.Section section, int questionCount) {
        int planned = section.plannedQuestions();
        if (questionCount <= 0) {
            return 0;
        }
        if (questionCount >= planned) {
            return section.getMinutes() * 60;
        }
        long minutes = Math.max(1, Math.round((double) section.getMinutes() * questionCount / planned));
        return (int) minutes * 60;
    }

    private Map<JlptQuestionType, Long> availableByType(JlptLevel level) {
        return questionRepository.countApprovedByType(level.name()).stream()
                .collect(Collectors.toMap(ExamQuestionRepository.TypeCount::getType,
                        ExamQuestionRepository.TypeCount::getCount));
    }

    /** Phần đầu tiên (theo thứ tự làm bài) chưa bắt đầu; null nếu đã bắt đầu hết. */
    private static ExamSection nextSection(ExamSitting sitting, List<UserExamAttempt> attempts) {
        Set<ExamSection> started = attempts.stream().map(UserExamAttempt::getSection).collect(Collectors.toSet());
        return sitting.sectionList().stream().filter(name -> !started.contains(name)).findFirst().orElse(null);
    }

    private static boolean allSectionsFinished(ExamSitting sitting, List<UserExamAttempt> attempts) {
        return nextSection(sitting, attempts) == null && attempts.stream().noneMatch(JlptExamService::inProgress);
    }

    private static boolean inProgress(UserExamAttempt attempt) {
        return attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS;
    }
}
