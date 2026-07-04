package com.cscen.forum.repo;

import com.cscen.forum.model.ModerationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModerationEventRepository extends JpaRepository<ModerationEvent, String> {

    List<ModerationEvent> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
