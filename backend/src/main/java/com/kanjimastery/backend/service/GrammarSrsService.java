package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.GrammarCardResponse;
import com.kanjimastery.backend.dto.GrammarPointStatusResponse;
import com.kanjimastery.backend.dto.GrammarQuestionResponse;
import com.kanjimastery.backend.dto.GrammarReviewRequest;
import com.kanjimastery.backend.dto.GrammarReviewResponse;
import com.kanjimastery.backend.dto.GrammarSessionResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.GrammarReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.UserGrammarSrs;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import com.kanjimastery.backend.repository.GrammarReviewLogRepository;
import com.kanjimastery.backend.repository.UserGrammarSrsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ôn ngữ pháp theo lịch: mỗi điểm ngữ pháp người học thêm vào là một thẻ, xếp lịch bằng {@link CardScheduler} như thẻ
 * từ vựng (SM-2 hay FSRS theo lựa chọn trong hồ sơ). Điểm đã có câu hỏi đã duyệt thì hỏi bằng một câu - server chấm,
 * đúng là "Nhớ", sai là "Quên"; điểm chưa có câu thì ôn bằng thẻ lật, người học tự chấm.
 * <p>
 * FSRS dùng tham số mặc định chứ không dùng bộ tham số đã tối ưu riêng cho người học: bộ đó học từ lịch sử ôn từ vựng,
 * còn ngữ pháp là loại kiến thức khác.
 */
@Service
@RequiredArgsConstructor
public class GrammarSrsService {

    /** Trần số thẻ đến hạn đọc cho một phiên (người nghỉ lâu có thể tồn nhiều thẻ). */
    static final int MAX_DUE_CARDS = 200;

    private final UserGrammarSrsRepository srsRepository;
    private final GrammarReviewLogRepository logRepository;
    private final GrammarPointRepository grammarPointRepository;
    private final ExamQuestionRepository questionRepository;
    private final CardScheduler cardScheduler;
    private final LearningProfileService learningProfileService;
    private final FuriganaService furiganaService;
    private final SrsProperties srsProperties;
    private final StudyCalendar calendar;

    /** Các điểm ngữ pháp của một cấp độ theo bài, kèm điểm nào người học đã đưa vào lịch ôn. */
    @Transactional(readOnly = true)
    public List<GrammarPointStatusResponse> points(Long userId, String level) {
        List<GrammarPoint> points = grammarPointRepository.findByJlptLevelOrderByLessonAscIdAsc(Levels.require(level));
        Map<Long, UserGrammarSrs> cards = srsRepository.findAllByUserIdAndGrammarPointIdIn(userId,
                        points.stream().map(GrammarPoint::getId).toList()).stream()
                .collect(Collectors.toMap(UserGrammarSrs::getGrammarPointId, Function.identity()));
        return points.stream().map(point -> {
            UserGrammarSrs card = cards.get(point.getId());
            return new GrammarPointStatusResponse(point.getId(), point.getLesson(), point.getPattern(),
                    point.getConnection(), point.getMeaningVi(), point.getExplanationVi(), card != null,
                    card == null ? null : card.getNextReviewAt());
        }).toList();
    }

    /**
     * Đưa các điểm ngữ pháp vào lịch ôn, đến hạn ngay (vẫn theo giới hạn điểm mới mỗi ngày). Điểm đã có giữ nguyên
     * tiến độ; id không tồn tại bị bỏ qua.
     */
    @Transactional
    public AddSrsCardsResponse addCards(Long userId, List<Long> grammarPointIds) {
        Set<Long> ids = grammarPointIds.stream().filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long existing = grammarPointRepository.countByIdIn(ids);
        int added = srsRepository.insertCardsIfAbsent(userId, ids, calendar.now());
        return new AddSrsCardsResponse(added, (int) existing - added);
    }

    /**
     * Phiên ôn hôm nay: các điểm đến hạn (dùng FSRS thì điểm dễ quên nhất trước), rồi các điểm mới theo thứ tự đã
     * thêm, tối đa {@code app.srs.grammar-new-per-day} điểm mới mỗi ngày học.
     */
    @Transactional(readOnly = true)
    public GrammarSessionResponse session(Long userId) {
        LocalDateTime now = calendar.now();
        SchedulingSettings settings = scheduling(userId);

        List<UserGrammarSrs> due = new ArrayList<>(srsRepository
                .findByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(userId, now,
                        PageRequest.of(0, MAX_DUE_CARDS)));
        if (settings.usesFsrs()) {
            due.sort(Comparator.comparingDouble(card -> cardScheduler.recall(card, now, settings.fsrs())));
        }
        long learnedToday = logRepository.countNewLearnedSince(userId, calendar.startOfToday());
        int newLeft = (int) Math.max(0, srsProperties.getGrammarNewPerDay() - learnedToday);
        List<UserGrammarSrs> cards = new ArrayList<>(due);
        if (newLeft > 0) {
            cards.addAll(srsRepository.findByUserIdAndLastReviewedAtIsNullOrderByIdAsc(userId,
                    PageRequest.of(0, newLeft)));
        }

        List<Long> pointIds = cards.stream().map(UserGrammarSrs::getGrammarPointId).toList();
        Map<Long, GrammarPoint> points = grammarPointRepository.findAllById(pointIds).stream()
                .collect(Collectors.toMap(GrammarPoint::getId, Function.identity()));
        Map<Long, GrammarQuestionResponse> questions = questionsFor(userId, pointIds);
        List<GrammarCardResponse> responses = cards.stream()
                .filter(card -> points.containsKey(card.getGrammarPointId()))
                .map(card -> {
                    GrammarPoint point = points.get(card.getGrammarPointId());
                    return new GrammarCardResponse(point.getId(), point.getJlptLevel().name(), point.getLesson(),
                            point.getPattern(), point.getConnection(), point.getMeaningVi(), point.getExplanationVi(),
                            card.getLastReviewedAt() == null, cardScheduler.previewIntervals(card, now, settings),
                            questions.get(point.getId()));
                })
                .toList();
        return new GrammarSessionResponse(responses,
                srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(userId, now),
                newLeft, srsRepository.countByUserIdAndLastReviewedAtIsNull(userId));
    }

    /**
     * Chấm một lần ôn và xếp lịch lần sau. Có {@code questionId}: server chấm đáp án đã chọn (đúng = Nhớ, sai = Quên);
     * không có: thẻ lật, dùng mức người học tự chấm. Hai lần chấm cùng lúc thì khoá lạc quan từ chối lần sau (409).
     */
    @Transactional
    public GrammarReviewResponse review(Long userId, GrammarReviewRequest request) {
        UserGrammarSrs card = srsRepository.findByUserIdAndGrammarPointId(userId, request.getGrammarPointId())
                .orElseThrow(() -> new ResourceNotFoundException("Điểm ngữ pháp này chưa có trong lịch ôn"));
        ExamQuestion question = null;
        String chosenOption = null;
        ReviewRating rating;
        boolean correct;
        if (request.getQuestionId() != null) {
            question = reviewQuestion(request.getQuestionId(), request.getGrammarPointId());
            if (request.getChosenOption() == null) {
                throw new BadRequestException("Chưa chọn đáp án");
            }
            chosenOption = request.getChosenOption().toUpperCase();
            correct = chosenOption.equals(question.getCorrectOption().toUpperCase());
            rating = correct ? ReviewRating.GOOD : ReviewRating.AGAIN;
        } else {
            if (request.getRating() == null) {
                throw new BadRequestException("Chưa chấm mức nhớ");
            }
            rating = ReviewRating.fromValue(request.getRating());
            correct = rating != ReviewRating.AGAIN;
        }

        LocalDateTime now = calendar.now();
        SchedulingSettings settings = scheduling(userId);
        logRepository.save(GrammarReviewLog.builder()
                .userId(userId)
                .grammarPointId(card.getGrammarPointId())
                .questionId(question == null ? null : question.getId())
                .chosenOption(chosenOption)
                .correct(correct)
                .rating(rating)
                .responseMs(ResponseTimeRater.normalize(request.getResponseMs()))
                .stateBefore(CardState.of(card))
                .retrievability(cardScheduler.recall(card, now, settings.fsrs()))
                .reviewedAt(now)
                .build());
        cardScheduler.apply(card, rating, now, settings);
        UserGrammarSrs saved = srsRepository.save(card);
        return new GrammarReviewResponse(correct, question == null ? null : question.getCorrectOption(),
                question == null ? null : question.getExplanation(), saved.getNextReviewAt(),
                saved.getReviewIntervalDays());
    }

    /** Thuật toán và tỉ lệ nhớ mong muốn theo hồ sơ người học, FSRS với tham số mặc định. */
    private SchedulingSettings scheduling(Long userId) {
        SchedulingSettings profile = learningProfileService.scheduling(userId);
        return new SchedulingSettings(profile.scheduler(), profile.desiredRetention(), Fsrs.withDefaults());
    }

    private Map<Long, GrammarQuestionResponse> questionsFor(Long userId, List<Long> pointIds) {
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> questionOfPoint = questionRepository.pickForGrammarReview(userId, pointIds).stream()
                .collect(Collectors.toMap(ExamQuestionRepository.GrammarReviewQuestion::getGrammarPointId,
                        ExamQuestionRepository.GrammarReviewQuestion::getQuestionId));
        Map<Long, ExamQuestion> questions = questionRepository.findAllById(questionOfPoint.values()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));
        return questionOfPoint.entrySet().stream()
                .filter(entry -> questions.containsKey(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    ExamQuestion question = questions.get(entry.getValue());
                    // Chỉ có câu ngữ pháp: furigana không lộ đáp án nào (như bài luyện ngữ pháp).
                    return GrammarQuestionResponse.from(question,
                            furiganaService.annotate(question.getSentence(), question.getHighlight(), null, false));
                }));
    }

    /** Câu đã duyệt, gắn với đúng điểm ngữ pháp đang ôn - không cho chấm bằng một câu bất kỳ. */
    private ExamQuestion reviewQuestion(Long questionId, Long grammarPointId) {
        return questionRepository.findAllWithLinksByIdIn(List.of(questionId)).stream().findFirst()
                .filter(question -> question.getStatus() == ExamQuestionStatus.APPROVED
                        && question.getGrammarPointIds().contains(grammarPointId))
                .orElseThrow(() -> new BadRequestException("Câu hỏi này không dùng để ôn điểm ngữ pháp đã chọn"));
    }
}
