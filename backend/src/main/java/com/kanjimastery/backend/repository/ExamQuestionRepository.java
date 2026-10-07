package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.JlptQuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import com.kanjimastery.backend.model.ExamQuestion;

public interface ExamQuestionRepository
        extends JpaRepository<ExamQuestion, Long>, JpaSpecificationExecutor<ExamQuestion> {

    @Query(value = """
            SELECT * FROM exam_questions WHERE jlpt_level = :level AND status = 'APPROVED' ORDER BY RANDOM() LIMIT :count
            """, nativeQuery = true)
    List<ExamQuestion> findRandomByLevel(@Param("level") String level, @Param("count") int count);

    @Query(value = """
            SELECT * FROM exam_questions
            WHERE jlpt_level = :level AND skill = :skill AND status = 'APPROVED'
            ORDER BY RANDOM() LIMIT :count
            """, nativeQuery = true)
    List<ExamQuestion> findRandomByLevelAndSkill(@Param("level") String level, @Param("skill") String skill,
                                                 @Param("count") int count);

    /**
     * Câu chưa phân loại kỹ năng (câu mẫu cũ). Câu ngữ pháp của đề JLPT cũng không gắn kỹ năng nhưng có dạng câu, và chỉ
     * dùng trong đề JLPT - thi nhanh chỉ hỏi từ vựng.
     */
    @Query(value = """
            SELECT * FROM exam_questions
            WHERE jlpt_level = :level AND skill IS NULL AND question_type IS NULL AND status = 'APPROVED'
            ORDER BY RANDOM() LIMIT :count
            """, nativeQuery = true)
    List<ExamQuestion> findRandomUnclassifiedByLevel(@Param("level") String level, @Param("count") int count);

    /**
     * Câu đã duyệt của một dạng câu trong đề JLPT ({@link com.kanjimastery.backend.model.JlptQuestionType}) cho một
     * người học: câu người đó chưa gặp trong lượt thi nào đứng trước (ngẫu nhiên), rồi tới câu gặp lâu nhất.
     */
    @Query(value = """
            SELECT q.* FROM exam_questions q
            LEFT JOIN (
                SELECT answer.question_id, MAX(attempt.started_at) AS seen_at
                FROM user_exam_answers answer
                JOIN user_exam_attempts attempt ON attempt.id = answer.attempt_id
                WHERE attempt.user_id = :userId
                GROUP BY answer.question_id
            ) seen ON seen.question_id = q.id
            WHERE q.jlpt_level = :level AND q.question_type = :type AND q.status = 'APPROVED'
            ORDER BY seen.seen_at NULLS FIRST, RANDOM()
            LIMIT :count
            """, nativeQuery = true)
    List<ExamQuestion> findForLearnerByLevelAndType(@Param("userId") Long userId, @Param("level") String level,
                                                    @Param("type") String type, @Param("count") int count);

    /** Số câu đã duyệt của một dạng câu JLPT. */
    interface TypeCount {
        JlptQuestionType getType();

        Long getCount();
    }

    @Query(value = """
            SELECT question_type AS "type", COUNT(*) AS "count" FROM exam_questions
            WHERE jlpt_level = :level AND question_type IS NOT NULL AND status = 'APPROVED'
            GROUP BY question_type
            """, nativeQuery = true)
    List<TypeCount> countApprovedByType(@Param("level") String level);

    /** Một câu thi đã sinh: kiểm tra từ nào, theo dạng câu JLPT (hoặc kỹ năng, với câu hỏi nghĩa của thi nhanh). */
    interface GeneratedQuestionWord {
        Long getKanjiId();

        String getKind();
    }

    @Query(value = """
            SELECT link.kanji_id AS "kanjiId", COALESCE(question.question_type, question.skill) AS "kind"
            FROM exam_questions question
            JOIN exam_question_kanji link ON link.question_id = question.id
            WHERE question.source = 'GENERATED' AND question.jlpt_level = :level
            """, nativeQuery = true)
    List<GeneratedQuestionWord> generatedQuestionWords(@Param("level") String level);

    /** Các câu hỏi kèm luôn từ vựng mỗi câu kiểm tra, trong một truy vấn. */
    @Query("SELECT DISTINCT q FROM ExamQuestion q LEFT JOIN FETCH q.kanjiIds WHERE q.id IN :ids")
    List<ExamQuestion> findAllWithWordsByIdIn(@Param("ids") Collection<Long> ids);

    /** Các câu hỏi kèm từ vựng và điểm ngữ pháp mỗi câu kiểm tra, trong một truy vấn. */
    @Query("""
            SELECT DISTINCT q FROM ExamQuestion q LEFT JOIN FETCH q.kanjiIds LEFT JOIN FETCH q.grammarPointIds
            WHERE q.id IN :ids
            """)
    List<ExamQuestion> findAllWithLinksByIdIn(@Param("ids") Collection<Long> ids);

    /** Số câu đề JLPT theo cấp độ, dạng câu và trạng thái duyệt. */
    interface BankCount {
        JlptLevel getLevel();

        JlptQuestionType getType();

        ExamQuestionStatus getStatus();

        Long getCount();
    }

    @Query(value = """
            SELECT jlpt_level AS "level", question_type AS "type", status AS "status", COUNT(*) AS "count"
            FROM exam_questions
            WHERE question_type IS NOT NULL
            GROUP BY jlpt_level, question_type, status
            """, nativeQuery = true)
    List<BankCount> countByLevelTypeAndStatus();

    /** Các từ đã có câu (chưa bị loại) của một dạng câu ở một cấp độ - để AI viết câu cho từ khác. */
    @Query(value = """
            SELECT DISTINCT link.kanji_id FROM exam_questions question
            JOIN exam_question_kanji link ON link.question_id = question.id
            WHERE question.jlpt_level = :level AND question.question_type = :type AND question.status <> 'REJECTED'
            """, nativeQuery = true)
    List<Long> findWordIdsWithQuestion(@Param("level") String level, @Param("type") String type);

    /** Câu đã duyệt, đứng riêng (không thuộc đoạn văn) của một cấp độ gắn với các điểm ngữ pháp - để luyện lại. */
    @Query(value = """
            SELECT q.* FROM exam_questions q
            WHERE q.jlpt_level = :level AND q.status = 'APPROVED' AND q.passage_id IS NULL
              AND q.id IN (SELECT link.question_id FROM exam_question_grammar link
                           WHERE link.grammar_point_id IN (:grammarPointIds))
            ORDER BY RANDOM() LIMIT :count
            """, nativeQuery = true)
    List<ExamQuestion> findRandomForGrammarPoints(@Param("level") String level,
                                                  @Param("grammarPointIds") Collection<Long> grammarPointIds,
                                                  @Param("count") int count);

    /** Câu trong đề của các câu hỏi gắn với một điểm ngữ pháp - để AI viết câu mới không trùng ý. */
    @Query("""
            SELECT q.sentence FROM ExamQuestion q JOIN q.grammarPointIds point
            WHERE point = :grammarPointId AND q.sentence IS NOT NULL
            """)
    List<String> findSentencesByGrammarPoint(@Param("grammarPointId") Long grammarPointId);

    /** Câu hỏi của các đoạn văn, kèm từ vựng và điểm ngữ pháp mỗi câu kiểm tra. */
    @Query("""
            SELECT DISTINCT q FROM ExamQuestion q LEFT JOIN FETCH q.kanjiIds LEFT JOIN FETCH q.grammarPointIds
            WHERE q.passageId IN :passageIds
            """)
    List<ExamQuestion> findAllWithLinksByPassageIdIn(@Param("passageIds") Collection<Long> passageIds);
}
