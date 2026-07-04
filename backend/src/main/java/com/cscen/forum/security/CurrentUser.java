package com.cscen.forum.security;

import com.cscen.forum.model.User;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final JwtService jwtService;
    private final UserRepository users;

    public CurrentUser(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    /** User id from the bearer token, or null — never rejects (public endpoints). */
    public String optionalUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return jwtService.verify(header.substring("Bearer ".length()));
    }

    public String requireUserId(HttpServletRequest request) {
        String userId = optionalUserId(request);
        if (userId == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return userId;
    }

    public User requireUser(HttpServletRequest request) {
        return users.findById(requireUserId(request))
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
    }

    /** For content-creating routes: requires auth AND a non-suspended account. */
    public User requireActiveUser(HttpServletRequest request) {
        User user = requireUser(request);
        if (user.isBanned()) {
            throw ApiException.forbidden("Your account is suspended due to repeated guideline violations");
        }
        return user;
    }

    public User requireAdmin(HttpServletRequest request) {
        User user = requireActiveUser(request);
        if (!"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("Admin access required");
        }
        return user;
    }
}
