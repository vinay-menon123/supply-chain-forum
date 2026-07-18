package com.cscen.forum.repo;

import com.cscen.forum.model.Question;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, String> {

    /** Used by the daily community activity job so a pooled topic is never posted twice. */
    boolean existsByTitle(String title);

    /** Recent threads still waiting for the moderator to verify an answer. */
    List<Question> findByAcceptedCommentIdIsNullAndCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    String FILTER = """
            (:q = '' or lower(q.title) like :like or lower(q.body) like :like)
            and (:tag = '' or q.tag = :tag)
            """;

    @Query("select q from Question q where " + FILTER + " order by q.createdAt desc")
    List<Question> searchNewest(@Param("q") String q, @Param("like") String like,
                                @Param("tag") String tag, Pageable pageable);

    @Query("select q from Question q left join Vote v on v.questionId = q.id where " + FILTER
            + " group by q order by count(v) desc, q.createdAt desc")
    List<Question> searchTop(@Param("q") String q, @Param("like") String like,
                             @Param("tag") String tag, Pageable pageable);

    @Query("select count(q) from Question q where " + FILTER)
    long searchCount(@Param("q") String q, @Param("like") String like, @Param("tag") String tag);

    List<Question> findByAuthorIdOrderByCreatedAtDesc(String authorId, Pageable pageable);

    long countByAuthorId(String authorId);

    @Query("select count(q) from Question q, Comment c where q.acceptedCommentId = c.id and c.authorId = :authorId")
    long countAcceptedForAuthor(@Param("authorId") String authorId);

    @Query(value = """
            select q.id from "Question" q
            left join "Vote" v on v."questionId" = q.id
            where q."createdAt" >= :since
            group by q.id
            order by count(v.id) desc, max(q."createdAt") desc
            limit 5
            """, nativeQuery = true)
    List<String> topQuestionIdsSince(@Param("since") java.time.Instant since);
}
