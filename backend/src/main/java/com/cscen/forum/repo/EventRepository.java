package com.cscen.forum.repo;

import com.cscen.forum.model.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findByStartsAtGreaterThanEqualOrderByStartsAtAsc(Instant from);

    List<Event> findByStartsAtBeforeOrderByStartsAtDesc(Instant before, Pageable pageable);
}
