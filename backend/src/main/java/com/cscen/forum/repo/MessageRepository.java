package com.cscen.forum.repo;

import com.cscen.forum.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {

    @Query("select m from Message m where m.fromId = :me or m.toId = :me order by m.createdAt desc")
    List<Message> findConversationsFeed(@Param("me") String me, Pageable pageable);

    long countByToIdAndReadAtIsNull(String toId);

    @Query("""
            select m from Message m
            where (m.fromId = :a and m.toId = :b) or (m.fromId = :b and m.toId = :a)
            order by m.createdAt asc
            """)
    List<Message> findThread(@Param("a") String a, @Param("b") String b, Pageable pageable);

    @Modifying
    @Transactional
    @Query("update Message m set m.readAt = :now where m.fromId = :from and m.toId = :to and m.readAt is null")
    int markRead(@Param("from") String from, @Param("to") String to, @Param("now") Instant now);
}
