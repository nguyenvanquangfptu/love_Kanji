package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.SrsProperties;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.dto.DailyCardResponse;
import com.kanjimastery.backend.dto.DailyPlanResponse;
import com.kanjimastery.backend.dto.ReviewRequest;
import com.kanjimastery.backend.dto.ReviewResponse;
import com.kanjimastery.backend.dto.SrsStatsResponse;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.HardWordsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.kanjimastery.backend.model.CardState;
import com.kanjimastery.backend.model.FsrsState;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewLog;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.Sm2State;
import com.kanjimastery.backend.model.UserKanjiSrs;
import com.kanjimastery.backend.repository.ReviewLogRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;

@Service
@RequiredArgsConstructor
public class SrsService {

    /** Khoảng ôn từ chừng này ngày trở lên thì coi là "đã thuộc". */
    static final int MASTERED_INTERVAL_DAYS_THRESHOLD = 21;
    /** Trần số thẻ đến hạn đọc cho một phiên ôn (người nghỉ lâu có thể tồn hàng nghìn thẻ). */
    private static final int MAX_DUE_CARDS = 2000;

    private final UserKanjiSrsRepository srsRepository;
    private final KanjiRepository kanjiRepository;
    private final CardScheduler cardScheduler;
    private final ReviewLogRepository reviewLogRepository;
    private final SrsProperties srsProperties;
    private final StudyPlanService studyPlanService;
    private final LearningProfileService learningProfileService;
    private final StudyCalendar calendar;

    /**
     * Một lần người học trả lời một từ.
     *
     * @param direction    hướng hỏi của trắc nghiệm, null với thẻ ôn tập
     * @param responseMs   đã qua {@link ResponseTimeRater#normalize(Integer)}
     * @param chosenAnswer đáp án đã chọn trong trắc nghiệm, null với thẻ ôn tập
     */
    public record Answer(ReviewSource source, QuizDirection direction, boolean correct, ReviewRating rating,
                         Integer responseMs,
                         String chosenAnswer) {
    }

    /**
     * Phiên ôn hôm nay theo {@link StudyPlanService}: thẻ ôn đến hạn xếp theo nguy cơ quên (dùng FSRS thì theo xác
     * suất còn nhớ, thấp nhất trước), tối đa bằng số thẻ kịp ôn trong thời gian người học có; từ mới tối đa bằng số
     * từ mới còn lại của hôm nay, xen giữa các thẻ ôn.
     * {@code extra} = ôn thêm: bỏ hai giới hạn trên, lấy mọi thẻ đến hạn.
     */
    public Page<DailyCardResponse> getDailyCards(Long userId, Pageable pageable, boolean extra) {
        LocalDateTime now = calendar.now();
        // Composite index idx_user_next_review (user_id, next_review_at).
        List<UserKanjiSrs> due = srsRepository.findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(
                userId, now, PageRequest.of(0, MAX_DUE_CARDS)).getContent();
        SchedulingSettings settings = learningProfileService.scheduling(userId);
        Comparator<UserKanjiSrs> riskiestFirst = settings.usesFsrs()
                ? Comparator.comparingDouble((UserKanjiSrs card) -> cardScheduler.recall(card, now, settings.fsrs()))
                : DailySessionOrder.mostOverdueFirst(now);
        List<UserKanjiSrs> session;
        if (extra) {
            session = DailySessionOrder.order(due, riskiestFirst, Integer.MAX_VALUE, Integer.MAX_VALUE);
        } else {
            DailyPlanResponse plan = studyPlanService.today(userId);
            session = DailySessionOrder.order(due, riskiestFirst, plan.getReviewsToday(), plan.getNewToday());
        }
        int from = (int) Math.min(pageable.getOffset(), session.size());
        List<UserKanjiSrs> pageCards = session.subList(from, Math.min(from + pageable.getPageSize(), session.size()));

        List<Long> kanjiIds = pageCards.stream().map(UserKanjiSrs::getKanjiId).toList();
        // Batch-fetch 1 lần duy nhất thay vì gọi findById trong vòng lặp (tránh N+1 Query).
        Map<Long, Kanji> kanjiById = kanjiRepository.findAllById(kanjiIds).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));

        List<DailyCardResponse> cards = pageCards.stream()
                .map(srs -> DailyCardResponse.builder()
                        .srsId(srs.getId())
                        .kanji(KanjiResponse.from(kanjiById.get(srs.getKanjiId())))
                        .repetitionCount(srs.getRepetitionCount())
                        .easinessFactor(srs.getEasinessFactor())
                        .reviewIntervalDays(srs.getReviewIntervalDays())
                        .nextReviewAt(srs.getNextReviewAt())
                        .lastReviewedAt(srs.getLastReviewedAt())
                        .lapseCount(srs.getLapseCount())
                        .hardWord(isHardWord(srs))
                        .personalNote(srs.getPersonalNote())
                        .newCard(srs.getLastReviewedAt() == null)
                        .intervals(cardScheduler.previewIntervals(srs, now, settings))
                        .build())
                .toList();
        return new PageImpl<>(hardWordsUpFrontOnly(cards), pageable, session.size());
    }

    /** Chỉ để vài từ khó đầu tiên ở chỗ cũ, các từ khó sau đó dồn xuống cuối phiên ôn để người học không bị ngợp. */
    private List<DailyCardResponse> hardWordsUpFrontOnly(List<DailyCardResponse> cards) {
        List<DailyCardResponse> ordered = new ArrayList<>(cards.size());
        List<DailyCardResponse> deferred = new ArrayList<>();
        int hardWords = 0;
        for (DailyCardResponse card : cards) {
            if (card.isHardWord() && ++hardWords > srsProperties.getHardWordsUpFront()) {
                deferred.add(card);
            } else {
                ordered.add(card);
            }
        }
        ordered.addAll(deferred);
        return ordered;
    }

    private boolean isHardWord(UserKanjiSrs card) {
        return card.getLapseCount() >= srsProperties.getHardWordLapses();
    }

    /** Từ khó của người học, quên nhiều lần nhất trước. */
    public HardWordsResponse getHardWords(Long userId) {
        List<UserKanjiSrs> cards = srsRepository
                .findByUserIdAndLapseCountGreaterThanEqualOrderByLapseCountDesc(userId, srsProperties.getHardWordLapses());
        Map<Long, Kanji> kanjiById = kanjiRepository.findAllById(cards.stream().map(UserKanjiSrs::getKanjiId).toList())
                .stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        return HardWordsResponse.builder()
                .lapseThreshold(srsProperties.getHardWordLapses())
                .words(cards.stream()
                        .map(card -> HardWordsResponse.Word.builder()
                                .kanji(KanjiResponse.from(kanjiById.get(card.getKanjiId())))
                                .lapseCount(card.getLapseCount())
                                .nextReviewAt(card.getNextReviewAt())
                                .personalNote(card.getPersonalNote())
                                .build())
                        .toList())
                .build();
    }

    /** Ghi chú/cách nhớ riêng của người học cho một từ trong lịch ôn của họ; để trống là xoá. */
    @Transactional
    public void saveNote(Long userId, Long kanjiId, String note) {
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, kanjiId)
                .orElseThrow(() -> new ResourceNotFoundException("Từ này chưa có trong Ôn tập của bạn"));
        card.setPersonalNote(StringUtils.hasText(note) ? note.trim() : null);
        srsRepository.save(card);
    }

    /** Id các từ khó của người học - nguồn cho trắc nghiệm chỉ gồm từ khó. */
    public List<Long> hardWordIds(Long userId) {
        return srsRepository
                .findByUserIdAndLapseCountGreaterThanEqualOrderByLapseCountDesc(userId, srsProperties.getHardWordLapses())
                .stream()
                .map(UserKanjiSrs::getKanjiId)
                .toList();
    }

    @Transactional
    public ReviewResponse submitReview(Long userId, ReviewRequest request) {
        if (!kanjiRepository.existsById(request.getKanjiId())) {
            throw new ResourceNotFoundException("Không tìm thấy Kanji với id: " + request.getKanjiId());
        }

        ReviewRating rating = ReviewRating.fromValue(request.getRating());
        Answer answer = new Answer(ReviewSource.FLASHCARD, null, rating != ReviewRating.AGAIN, rating,
                ResponseTimeRater.normalize(request.getResponseMs()), null);
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, request.getKanjiId()).orElse(null);
        UserKanjiSrs saved = schedule(userId, request.getKanjiId(), card, answer, calendar.now(),
                learningProfileService.scheduling(userId));

        return ReviewResponse.builder()
                .kanjiId(saved.getKanjiId())
                .repetitionCount(saved.getRepetitionCount())
                .easinessFactor(saved.getEasinessFactor())
                .reviewIntervalDays(saved.getReviewIntervalDays())
                .nextReviewAt(saved.getNextReviewAt())
                .build();
    }

    /**
     * Ghi một câu trắc nghiệm và cập nhật lịch ôn:
     * <ul>
     *   <li>Sai, từ chưa có trong lịch ôn: thêm vào như một từ đã gặp và vừa quên (không phải từ mới, nên không bị
     *       giới hạn số từ mới mỗi ngày giữ lại), đến hạn ngay để ôn trong phiên hôm nay.</li>
     *   <li>Sai, từ đã có: tính là một lần "Quên" - học lại từ đầu như khi lật thẻ.</li>
     *   <li>Đúng, thẻ đã đến hạn: tính là một lần ôn với {@link Answer#rating()}.</li>
     *   <li>Đúng, thẻ chưa đến hạn (hoặc chưa có trong lịch ôn): chỉ ghi lại. SM-2 không tính tới việc ôn sớm,
     *       nên tính vào lịch sẽ nhân khoảng ôn lên dù mới ôn hôm qua.</li>
     * </ul>
     *
     * @return lần ôn tiếp theo, rỗng nếu từ không nằm trong lịch ôn
     */
    @Transactional
    public Optional<LocalDateTime> recordQuizAnswer(Long userId, Long kanjiId, Answer answer) {
        LocalDateTime now = calendar.now();
        SchedulingSettings settings = learningProfileService.scheduling(userId);
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, kanjiId).orElse(null);
        if (card == null) {
            log(userId, kanjiId, null, answer, false, now, settings.fsrs());
            if (answer.correct()) {
                return Optional.empty();
            }
            srsRepository.insertSeenCardIfAbsent(userId, kanjiId, now);
            return Optional.of(now);
        }
        if (!answer.correct() || !card.getNextReviewAt().isAfter(now)) {
            return Optional.of(schedule(userId, kanjiId, card, answer, now, settings).getNextReviewAt());
        }
        log(userId, kanjiId, card, answer, false, now, settings.fsrs());
        return Optional.of(card.getNextReviewAt());
    }

    /**
     * Hoàn tác lần chấm thẻ gần nhất của một từ (bấm nhầm): thẻ trở về đúng trạng thái trước lần chấm - SM-2, FSRS, ngày
     * đến hạn, số lần quên - còn ghi chú riêng giữ nguyên; thẻ do chính lần chấm đó tạo ra thì bị xoá. Lần chấm bị xoá
     * khỏi lịch sử ôn nên mọi thống kê không còn tính nó. Gọi lần lượt cho từng từ để hoàn tác nhiều thẻ.
     * <p>
     * Chỉ được khi lần chấm đó vẫn là lần trả lời mới nhất của từ (chưa ôn lại, chưa làm trắc nghiệm sau đó) và chưa quá
     * {@code app.srs.undo-minutes} phút. Hai lần hoàn tác cùng lúc thì khoá lạc quan của thẻ từ chối lần sau (409).
     */
    @Transactional
    public void undoReview(Long userId, Long kanjiId) {
        ReviewLog last = reviewLogRepository.findFirstByUserIdAndKanjiIdOrderByReviewedAtDescIdDesc(userId, kanjiId)
                .orElseThrow(() -> new BadRequestException("Từ này chưa có lần chấm nào để hoàn tác"));
        if (last.getSource() != ReviewSource.FLASHCARD) {
            throw new BadRequestException("Từ này đã được trả lời lại sau lần chấm thẻ, không hoàn tác được nữa");
        }
        // Dòng ghi trước V36 không có trạng thái thẻ trước đó (card_existed_before NULL) nên không khôi phục được.
        if (last.getCardExistedBefore() == null) {
            throw new BadRequestException("Lần chấm này có từ trước khi có tính năng hoàn tác, không hoàn tác được");
        }
        int undoMinutes = srsProperties.getUndoMinutes();
        if (last.getReviewedAt().isBefore(calendar.now().minusMinutes(undoMinutes))) {
            throw new BadRequestException("Chỉ hoàn tác được trong " + undoMinutes + " phút sau khi chấm thẻ");
        }
        UserKanjiSrs card = srsRepository.findByUserIdAndKanjiId(userId, kanjiId)
                .orElseThrow(() -> new ResourceNotFoundException("Từ này không còn trong Ôn tập của bạn"));

        if (last.getCardExistedBefore()) {
            card.setSm2(new Sm2State(last.getRepetitionBefore(), last.getEfBefore()));
            card.setReviewIntervalDays(last.getIntervalBefore());
            card.setNextReviewAt(last.getNextReviewBefore());
            card.setLastReviewedAt(last.getLastReviewedBefore());
            card.setLapseCount(last.getLapsesBefore());
            card.setFsrs(last.getStabilityBefore() == null ? null
                    : new FsrsState(last.getStabilityBefore(), last.getDifficultyBefore()));
            srsRepository.save(card);
        } else {
            srsRepository.delete(card);
        }
        reviewLogRepository.delete(last);
    }

    /**
     * Tính lịch ôn mới (thẻ chưa có thì tạo) và ghi lại lần trả lời trong cùng transaction.
     * "Quên" một thẻ đang ôn bình thường (đã nhớ lại được kể từ lần quên trước) được đếm vào số lần quên - đủ số lần
     * thì thành từ khó. Quên tiếp khi đang học lại không tính thêm: trắc nghiệm thích ứng hay hỏi lại từ vừa sai, nên
     * sai nhiều lần liền trong một buổi vẫn chỉ là một lần quên - và trong cùng ngày học, SM-2 cũng chỉ trừ hệ số dễ
     * (EF) một lần.
     */
    private UserKanjiSrs schedule(Long userId, Long kanjiId, UserKanjiSrs card, Answer answer, LocalDateTime now,
                                  SchedulingSettings settings) {
        log(userId, kanjiId, card, answer, true, now, settings.fsrs());
        UserKanjiSrs srs = card != null ? card : UserKanjiSrs.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .nextReviewAt(now)
                .build();
        cardScheduler.apply(srs, answer.rating(), now, settings);
        return srsRepository.save(srs);
    }

    /**
     * Ghi kèm trạng thái thẻ ngay trước lần trả lời ({@code card} null = chưa có trong lịch ôn) và xác suất nhớ FSRS
     * dự đoán lúc đó.
     */
    private void log(Long userId, Long kanjiId, UserKanjiSrs card, Answer answer, boolean scheduled, LocalDateTime now,
                     Fsrs fsrs) {
        reviewLogRepository.save(ReviewLog.builder()
                .userId(userId)
                .kanjiId(kanjiId)
                .source(answer.source())
                .direction(answer.direction())
                .correct(answer.correct())
                .rating(answer.rating())
                .responseMs(answer.responseMs())
                .chosenAnswer(answer.chosenAnswer())
                .stateBefore(CardState.of(card))
                .efBefore(card == null ? null : card.getEasinessFactor())
                .intervalBefore(card == null ? null : card.getReviewIntervalDays())
                .cardExistedBefore(card != null)
                .repetitionBefore(card == null ? null : card.getRepetitionCount())
                .lapsesBefore(card == null ? null : card.getLapseCount())
                .nextReviewBefore(card == null ? null : card.getNextReviewAt())
                .lastReviewedBefore(card == null ? null : card.getLastReviewedAt())
                .stabilityBefore(card == null ? null : card.getStability())
                .difficultyBefore(card == null ? null : card.getDifficulty())
                .retrievability(cardScheduler.recall(card, now, fsrs))
                .scheduled(scheduled)
                .reviewedAt(now)
                .build());
    }

    /**
     * Đưa các từ (cả một bài, hoặc các từ làm sai trong trắc nghiệm) vào lịch ôn SRS.
     * Từ mới đến hạn ôn ngay; từ đã có trong lịch ôn giữ nguyên tiến độ; id không tồn tại bị bỏ qua.
     */
    @Transactional
    public AddSrsCardsResponse addCards(Long userId, List<Long> kanjiIds) {
        Set<Long> uniqueIds = new HashSet<>(kanjiIds);
        long existingWords = kanjiRepository.countByIdIn(uniqueIds);
        int added = srsRepository.insertCardsIfAbsent(userId, uniqueIds, calendar.now());
        return AddSrsCardsResponse.builder()
                .added(added)
                .alreadyInReview((int) existingWords - added)
                .build();
    }

    public SrsTagStatusResponse getTagStatus(Long userId, Long tagId) {
        return SrsTagStatusResponse.builder()
                .totalWords(kanjiRepository.countByTags_Id(tagId))
                .inReview(srsRepository.countInReviewByTag(userId, tagId))
                .build();
    }

    public SrsStatsResponse getStats(Long userId) {
        LocalDateTime now = calendar.now();
        long total = srsRepository.countByUserId(userId);
        // Từ mới chưa học lần nào không phải "cần ôn": chúng chờ theo số từ mới mỗi ngày của kế hoạch. Đếm cả vào đây
        // thì vừa thêm một bài 50 từ, ô "Cần ôn" đã báo 50 dù kế hoạch hôm nay không có thẻ ôn nào.
        long newWaiting = srsRepository.countByUserIdAndLastReviewedAtIsNull(userId);
        long due = srsRepository.countByUserIdAndLastReviewedAtIsNotNullAndNextReviewAtLessThanEqual(userId, now);
        long mastered = srsRepository.countByUserIdAndNextReviewAtAfterAndReviewIntervalDaysGreaterThanEqual(
                userId, now, MASTERED_INTERVAL_DAYS_THRESHOLD);
        long learning = total - newWaiting - due - mastered;

        return SrsStatsResponse.builder()
                .totalCardsStarted(total)
                .dueForReview(due)
                .stillLearning(Math.max(learning, 0))
                .deeplyMemorized(mastered)
                .hardWords(srsRepository.countByUserIdAndLapseCountGreaterThanEqual(userId, srsProperties.getHardWordLapses()))
                .build();
    }
}
