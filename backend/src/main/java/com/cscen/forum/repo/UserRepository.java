package com.cscen.forum.repo;

import com.cscen.forum.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByUsername(String username);

    @Query("select u from User u where u.flagCount > 0 or u.isBanned = true order by u.isBanned desc, u.flagCount desc")
    List<User> findFlagged();

    List<User> findByOpenToMentorTrueAndIsBannedFalseOrderByCreatedAtAsc();

    List<User> findBySeekingMentorTrueAndIsBannedFalseOrderByCreatedAtAsc();

    List<User> findByIsBannedFalse();

    @Query(value = """
            select * from (
              select u.id, u.username, u.name, u."avatarUrl", u."memberType", u.role,
                (select count(*) from "Question" q where q."authorId" = u.id) as questions,
                (select count(*) from "Comment" c where c."authorId" = u.id) as comments,
                (select count(*) from "Vote" v join "Question" q2 on q2.id = v."questionId" where q2."authorId" = u.id) as upvotes,
                (select count(*) from "Question" q3 join "Comment" c2 on q3."acceptedCommentId" = c2.id where c2."authorId" = u.id) as accepted
              from "User" u
              where u."isBanned" = false
            ) t
            order by (t.questions * 5 + t.comments * 2 + t.upvotes * 10 + t.accepted * 15) desc, t.username asc
            limit :limit
            """, nativeQuery = true)
    List<Object[]> leaderboard(@Param("limit") int limit);
}
