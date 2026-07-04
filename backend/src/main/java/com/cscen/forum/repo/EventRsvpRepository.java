package com.cscen.forum.repo;

import com.cscen.forum.model.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, String> {

    Optional<EventRsvp> findByUserIdAndEventId(String userId, String eventId);

    long countByEventId(String eventId);

    List<EventRsvp> findByUserIdAndEventIdIn(String userId, Collection<String> eventIds);

    @Query("select r.eventId, count(r) from EventRsvp r where r.eventId in :ids group by r.eventId")
    List<Object[]> countByEventIds(@Param("ids") Collection<String> ids);
}
