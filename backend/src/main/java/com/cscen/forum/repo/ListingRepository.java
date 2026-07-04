package com.cscen.forum.repo;

import com.cscen.forum.model.Listing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, String> {

    @Query("""
            select l from Listing l where
            (:kind = '' or l.kind = :kind)
            and (:category = '' or l.category = :category)
            order by l.createdAt desc
            """)
    List<Listing> search(@Param("kind") String kind, @Param("category") String category,
                         Pageable pageable);
}
