package com.cscen.forum.repo;

import com.cscen.forum.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, String> {

    Optional<Vote> findByUserIdAndQuestionId(String userId, String questionId);

    long countByQuestionId(String questionId);

    List<Vote> findByUserIdAndQuestionIdIn(String userId, Collection<String> questionIds);

    @Query("select v.questionId, count(v) from Vote v where v.questionId in :ids group by v.questionId")
    List<Object[]> countByQuestionIds(@Param("ids") Collection<String> ids);

    @Query("select count(v) from Vote v, Question q where v.questionId = q.id and q.authorId = :authorId")
    long countReceivedByAuthor(@Param("authorId") String authorId);

    Optional<Vote> findByUserIdAndCommentId(String userId, String commentId);

    long countByCommentId(String commentId);

    List<Vote> findByUserIdAndCommentIdIn(String userId, Collection<String> commentIds);

    @Query("select v.commentId, count(v) from Vote v where v.commentId in :ids group by v.commentId")
    List<Object[]> countByCommentIds(@Param("ids") Collection<String> ids);
}
