package com.cscen.forum.repo;

import com.cscen.forum.model.Job;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {

    String FILTER = """
            (:q = '' or lower(j.title) like :like or lower(j.company) like :like or lower(j.location) like :like)
            and (:tag = '' or j.tag = :tag)
            and (:type = '' or j.employmentType = :type)
            """;

    @Query("select j from Job j where " + FILTER + " order by j.createdAt desc")
    List<Job> search(@Param("q") String q, @Param("like") String like,
                     @Param("tag") String tag, @Param("type") String type, Pageable pageable);

    @Query("select count(j) from Job j where " + FILTER)
    long searchCount(@Param("q") String q, @Param("like") String like,
                     @Param("tag") String tag, @Param("type") String type);
}
