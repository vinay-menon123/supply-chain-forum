package com.cscen.forum.repo;

import com.cscen.forum.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    List<Comment> findByQuestionIdOrderByCreatedAtAsc(String questionId);

    long countByAuthorId(String authorId);

    @Query("select distinct c.questionId from Comment c where c.authorId = :authorId")
    List<String> questionIdsCommentedBy(@Param("authorId") String authorId);

    @Query("select c.questionId, count(c) from Comment c where c.questionId in :ids group by c.questionId")
    List<Object[]> countByQuestionIds(@Param("ids") Collection<String> ids);
}
