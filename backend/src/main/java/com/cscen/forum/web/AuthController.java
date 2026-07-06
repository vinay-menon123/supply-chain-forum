package com.cscen.forum.web;

import com.cscen.forum.model.User;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.security.JwtService;
import com.cscen.forum.service.AiModerationClient;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.QuestionService;
import com.cscen.forum.service.MailService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public static final Set<String> MEMBER_TYPES = Set.of(
            "ACADEMICIAN", "PROFESSIONAL", "RESEARCHER", "STUDENT",
            "INDUSTRY_PARTNER", "STARTUP_TECH_PARTNER");

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()\\-]{7,20}$");

    // Expiring OTP storage keyed by email. Codes are valid for OTP_TTL and are
    // single-use (removed on the first successful verification).
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private record OtpEntry(String code, Instant expiresAt) {}
    private static final ConcurrentHashMap<String, OtpEntry> otpStorage = new ConcurrentHashMap<>();

    /** Verifies a submitted code against the stored one, honouring expiry, and consumes it. */
    private static boolean consumeOtp(String email, String submitted) {
        if (email == null || submitted == null) {
            return false;
        }
        OtpEntry entry = otpStorage.get(email);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())
                || !entry.code().equals(submitted.trim())) {
            if (entry != null && entry.expiresAt().isBefore(Instant.now())) {
                otpStorage.remove(email);
            }
            return false;
        }
        otpStorage.remove(email);
        return true;
    }

    private final UserRepository users;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AiModerationClient ai;
    private final MailService mailService;
    private final List<String> adminEmails;
    // Dev-only convenience: when true (local), an OTP that couldn't be emailed is
    // returned in the API response so testing needs no inbox. MUST stay false in
    // production — otherwise the "email verification" is trivially bypassable.
    private final boolean exposeDevOtp;

    public AuthController(UserRepository users, JwtService jwtService,
                          CurrentUser currentUser, AiModerationClient ai,
                          MailService mailService, Environment env) {
        this.users = users;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.ai = ai;
        this.mailService = mailService;
        this.exposeDevOtp = env.getProperty("EXPOSE_DEV_OTP", Boolean.class, false);
        this.adminEmails = Arrays.stream(env.getProperty("ADMIN_EMAILS", "").split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .toList();
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("googleClientId", "");
    }

    public record SendOtpRequest(String email, String intent) {}

    @PostMapping("/send-otp")
    public Map<String, Object> sendOtp(@RequestBody SendOtpRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw ApiException.badRequest("Email is required");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Login flow: don't send a code to an address that has no account. Tell
        // the client so it can redirect the user to Create Account instead.
        if ("login".equalsIgnoreCase(request.intent()) && users.findByEmail(email).isEmpty()) {
            return Map.of(
                "success", false,
                "notRegistered", true,
                "message", "No account is registered with " + email + ". Please create an account.");
        }

        // Generate a 6-digit OTP code
        String code = String.format("%06d", new java.util.Random().nextInt(1000000));
        otpStorage.put(email, new OtpEntry(code, Instant.now().plus(OTP_TTL)));
        
        // Output code directly to console logs for local developer offline testing
        log.info("OTP SEND SIMULATOR: Send OTP to {}: Code = {}", email, code);
        
        boolean mailSent = false;
        if (mailService.isEnabled()) {
            String subject = "Verification Code - CSCE Nexus";
            String html = "<p>Your email verification code is: <strong>" + code + "</strong></p>" +
                          "<p>This code will expire in 5 minutes.</p>";
            mailSent = mailService.sendOne(email, subject, html);
        }
        
        if (mailSent) {
            return Map.of("success", true, "message", "Verification code sent to " + email);
        }

        // Mail wasn't sent (email disabled or the send failed).
        if (exposeDevOtp) {
            // Local/dev only: hand the code back so testing needs no real inbox.
            return Map.of(
                "success", true,
                "message", "Verification code sent to " + email + " (Simulated)",
                "devOtp", code
            );
        }
        // Production: never leak the code. Fail closed so verification can't be bypassed.
        log.warn("OTP email to {} could not be sent and EXPOSE_DEV_OTP is off — failing closed", email);
        return Map.of(
            "success", false,
            "message", "We couldn't send a verification code to " + email
                + " right now. Please try again in a few minutes."
        );
    }

    public record RegisterRequest(
        String username,
        String email,
        String password,
        String firstName,
        String lastName,
        String organization,
        String position,
        String linkedinUrl,
        String phone,
        String memberType,
        String otp,
        String topics
    ) {}

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        if (request == null || request.email() == null || request.otp() == null) {
            throw ApiException.badRequest("Missing email or verification code");
        }
        
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // Verify OTP code (single-use, expires after OTP_TTL)
        if (!consumeOtp(email, request.otp())) {
            throw ApiException.badRequest("Invalid or expired verification code (OTP)");
        }

        // Check uniqueness constraints
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw ApiException.badRequest("Username is already taken");
        }
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.badRequest("Email is already registered");
        }

        User user = User.create();
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setName(request.firstName() + " " + request.lastName());
        user.setPasswordHash(hashPassword(request.password()));
        user.setPhone(request.phone());
        user.setLinkedinUrl(request.linkedinUrl());
        user.setOrganization(request.organization());
        user.setPosition(request.position());
        user.setVerifyStatus("PENDING");
        user.setTopics(normalizeTopics(request.topics()));

        // Map auto-attached roles logic
        String memberType = request.memberType();
        user.setMemberType(memberType);
        
        String role = adminEmails.contains(email) ? "ADMIN" : "USER";
        user.setRole(role);
        
        // Set headline field and moderate relevance
        String headline = request.position() + " at " + request.organization();
        user.setHeadline(headline);
        
        final User finalUser = user;
        if (!headline.isBlank()) {
            ai.isSupplyChainRelevant(headline, request.linkedinUrl()).ifPresent(relevant ->
                    finalUser.setVerifyStatus(relevant ? "APPROVED" : "REJECTED"));
        }
        
        User savedUser = users.save(user);
        return Map.of("token", jwtService.sign(savedUser.getId()), "user", Json.privateUser(savedUser));
    }

    public record LoginRequest(
        String usernameOrEmail,
        String password,
        String otp
    ) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        if (request == null || request.usernameOrEmail() == null || request.password() == null) {
            throw ApiException.badRequest("Missing username/email or password");
        }
        
        String input = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(input)
                .or(() -> users.findByUsername(input))
                .orElseThrow(() -> ApiException.badRequest("Invalid credentials"));
                
        if (user.getPasswordHash() == null || !user.getPasswordHash().equals(hashPassword(request.password()))) {
            throw ApiException.badRequest("Invalid credentials");
        }
        
        if (request.otp() == null || request.otp().isBlank()) {
            throw ApiException.badRequest("Verification code (OTP) is required");
        }

        if (!consumeOtp(user.getEmail(), request.otp())) {
            throw ApiException.badRequest("Invalid or expired verification code (OTP)");
        }

        return Map.of("token", jwtService.sign(user.getId()), "user", Json.privateUser(user));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public record ProfileUpdate(String memberType, String phone, String organization,
                                Boolean openToMentor, Boolean seekingMentor,
                                String topics, String linkedinUrl, String headline, String bio,
                                Boolean notifyAllQuestions) {
    }

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
        // Only update the "notify me about every question" flag when the caller
        // sends it. The Welcome and Profile screens submit partial updates that
        // omit this field, and we must not silently reset the user's preference.
        if (request.notifyAllQuestions() != null) {
            user.setNotifyAllQuestions(request.notifyAllQuestions());
        }

        user.setTopics(normalizeTopics(request.topics()));

        String linkedin = request.linkedinUrl() == null ? "" : request.linkedinUrl().trim();
        if (!linkedin.isEmpty() && !linkedin.toLowerCase(Locale.ROOT).contains("linkedin.com/")) {
            throw ApiException.badRequest("Enter a valid LinkedIn profile URL (or leave it empty)");
        }
        user.setLinkedinUrl(linkedin.isEmpty() ? null : linkedin);

        String headline = request.headline() == null ? "" : request.headline().trim();
        if (headline.length() > 160) {
            throw ApiException.badRequest("Headline is too long");
        }
        boolean headlineChanged = !headline.equals(user.getHeadline() == null ? "" : user.getHeadline());
        user.setHeadline(headline.isEmpty() ? null : headline);

        String bio = request.bio() == null ? "" : request.bio().trim();
        if (bio.length() > 600) {
            throw ApiException.badRequest("Bio is too long (max 600 characters)");
        }
        user.setBio(bio.isEmpty() ? null : bio);

        if (!headline.isEmpty() && (headlineChanged || "PENDING".equals(user.getVerifyStatus()))) {
            ai.isSupplyChainRelevant(headline, linkedin).ifPresent(relevant ->
                    user.setVerifyStatus(relevant ? "APPROVED" : "REJECTED"));
        }

        return Map.of("user", Json.privateUser(users.save(user)));
    }

    private static String normalizeTopics(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> valid = Arrays.stream(raw.split(","))
                .map(t -> t.trim().toUpperCase(Locale.ROOT))
                .filter(QuestionService.TAGS::contains)
                .distinct()
                .toList();
        return valid.isEmpty() ? null : String.join(",", valid);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest http) {
        return Map.of("user", Json.privateUser(currentUser.requireUser(http)));
    }
}
