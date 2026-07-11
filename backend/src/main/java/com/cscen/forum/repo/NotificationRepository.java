package com.cscen.forum.repo;

import com.cscen.forum.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(String userId);

    @Modifying
    @Transactional
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    void markAllRead(@Param("userId") String userId, @Param("now") Instant now);
}
