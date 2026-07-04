package com.cscen.forum.web;

import com.cscen.forum.model.User;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.security.JwtService;
import com.cscen.forum.service.Json;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final Set<String> MEMBER_TYPES = Set.of(
            "ACADEMICIAN", "PROFESSIONAL", "RESEARCHER", "STUDENT",
            "INDUSTRY_PARTNER", "STARTUP_TECH_PARTNER");

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()\\-]{7,20}$");

    private final UserRepository users;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final String googleClientId;
    private final List<String> adminEmails;

    public AuthController(UserRepository users, JwtService jwtService,
                          CurrentUser currentUser, Environment env) {
        this.users = users;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.googleClientId = env.getProperty("GOOGLE_CLIENT_ID", "");
        // Comma-separated list; authoritative on every sign-in so edits to the
        // env var promote/demote users on their next login
        this.adminEmails = Arrays.stream(env.getProperty("ADMIN_EMAILS", "").split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .toList();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("googleClientId", googleClientId);
    }

    public record GoogleLogin(String credential) {
    }

    @PostMapping("/google")
    public Map<String, Object> google(@RequestBody GoogleLogin request) {
        if (googleClientId.isEmpty()) {
            throw new ApiException(503, "Google Sign-In is not configured (set GOOGLE_CLIENT_ID)");
        }
        if (request == null || request.credential() == null || request.credential().isBlank()) {
            throw ApiException.badRequest("Missing Google credential");
        }

        GoogleIdToken.Payload payload;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier
                    .Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(List.of(googleClientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(request.credential());
            payload = idToken == null ? null : idToken.getPayload();
        } catch (Exception e) {
            payload = null;
        }
        if (payload == null || payload.getSubject() == null || payload.getEmail() == null) {
            throw ApiException.unauthorized("Invalid Google credential");
        }
        if (Boolean.FALSE.equals(payload.getEmailVerified())) {
            throw ApiException.unauthorized("Google email is not verified");
        }

        String email = payload.getEmail().toLowerCase(Locale.ROOT);
        String role = adminEmails.contains(email) ? "ADMIN" : "USER";

        User user = users.findByGoogleId(payload.getSubject())
                .or(() -> users.findByEmail(email))
                .orElse(null);
        if (user == null) {
            user = User.create();
            user.setEmail(email);
            user.setUsername(uniqueUsername(email));
        }
        user.setGoogleId(payload.getSubject());
        user.setRole(role);
        if (payload.get("name") instanceof String name && !name.isBlank()) {
            user.setName(name);
        }
        if (payload.get("picture") instanceof String picture && !picture.isBlank()) {
            user.setAvatarUrl(picture);
        }
        user = users.save(user);

        return Map.of("token", jwtService.sign(user.getId()), "user", Json.privateUser(user));
    }

    public record ProfileUpdate(String memberType, String phone, String organization,
                                Boolean openToMentor, Boolean seekingMentor) {
    }

    /** Onboarding: member type (required) + optional phone / organization. */
    @PostMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody ProfileUpdate request,
                                             HttpServletRequest http) {
        User user = currentUser.requireUser(http);

        String memberType = request.memberType() == null ? "" : request.memberType().trim();
        if (!MEMBER_TYPES.contains(memberType)) {
            throw ApiException.badRequest("Please choose how you'll participate in the community");
        }
        user.setMemberType(memberType);

        String phone = request.phone() == null ? "" : request.phone().trim();
        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("Enter a valid phone number (or leave it empty)");
        }
        user.setPhone(phone.isEmpty() ? null : phone);

        String organization = request.organization() == null ? "" : request.organization().trim();
        if (organization.length() > 120) {
            throw ApiException.badRequest("Organization name is too long");
        }
        user.setOrganization(organization.isEmpty() ? null : organization);

        user.setOpenToMentor(Boolean.TRUE.equals(request.openToMentor()));
        user.setSeekingMentor(Boolean.TRUE.equals(request.seekingMentor()));

        return Map.of("user", Json.privateUser(users.save(user)));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest http) {
        return Map.of("user", Json.privateUser(currentUser.requireUser(http)));
    }

    private String uniqueUsername(String email) {
        String base = email.split("@")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        if (base.length() < 3) {
            base = "user_" + base;
        }
        String candidate = base;
        for (int suffix = 2; users.existsByUsername(candidate); suffix++) {
            candidate = base + suffix;
        }
        return candidate;
    }
}
