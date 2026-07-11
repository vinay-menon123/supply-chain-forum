package com.cscen.forum.repo;

import com.cscen.forum.model.Template;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TemplateRepository extends JpaRepository<Template, String> {

    String FILTER = """
            (:q = '' or lower(t.title) like :like or lower(t.description) like :like)
            and (:category = '' or t.category = :category)
            """;

    @Query("select t from Template t where " + FILTER + " order by t.createdAt desc")
    List<Template> search(@Param("q") String q, @Param("like") String like,
                          @Param("category") String category, Pageable pageable);
}
