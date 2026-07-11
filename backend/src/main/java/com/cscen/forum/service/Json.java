package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Job;
import com.cscen.forum.model.Notification;
import com.cscen.forum.model.Template;
import com.cscen.forum.model.User;

import java.util.LinkedHashMap;
import java.util.Map;

/** Response-shape helpers kept identical to the original Node API. */
public final class Json {

    private Json() {
    }

    public static Map<String, Object> author(User user) {
        Map<String, Object> json = new LinkedHashMap<>();
        if (user == null) {
            return json;
        }
        json.put("id", user.getId());
        json.put("username", user.getUsername());
        json.put("avatarUrl", user.getAvatarUrl());
        json.put("memberType", user.getMemberType());
        json.put("pro", user.isPro());
        return json;
    }

    /** Public profile view — no email or phone. */
    public static Map<String, Object> publicUser(User user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("username", user.getUsername());
        json.put("name", user.getName());
        json.put("avatarUrl", user.getAvatarUrl());
        json.put("role", user.getRole());
        json.put("memberType", user.getMemberType());
        json.put("organization", user.getOrganization());
        json.put("headline", user.getHeadline());
        json.put("bio", user.getBio());
        json.put("linkedinUrl", user.getLinkedinUrl());
        json.put("verifyStatus", user.getVerifyStatus());
        json.put("openToMentor", user.isOpenToMentor());
        json.put("seekingMentor", user.isSeekingMentor());
        json.put("plan", user.getPlan());
        json.put("pro", user.isPro());
        json.put("createdAt", user.getCreatedAt());
        return json;
    }

    /** Self view — includes contact details and notification preferences. */
    public static Map<String, Object> privateUser(User user) {
        Map<String, Object> json = publicUser(user);
        json.put("email", user.getEmail());
        json.put("phone", user.getPhone());
        json.put("topics", user.getTopics());
        json.put("notifyAllQuestions", user.isNotifyAllQuestions());
        json.put("planExpiresAt", user.getPlanExpiresAt());
        json.put("isBanned", user.isBanned());
        return json;
    }

    public static Map<String, Object> notification(Notification n, User actor) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", n.getId());
        json.put("type", n.getType());
        json.put("text", n.getText());
        json.put("questionId", n.getQuestionId());
        json.put("commentId", n.getCommentId());
        json.put("read", n.getReadAt() != null);
        json.put("createdAt", n.getCreatedAt());
        json.put("actor", actor == null ? null : author(actor));
        return json;
    }

    public static Map<String, Object> job(Job job, User author) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", job.getId());
        json.put("title", job.getTitle());
        json.put("company", job.getCompany());
        json.put("location", job.getLocation());
        json.put("employmentType", job.getEmploymentType());
        json.put("tag", job.getTag());
        json.put("description", job.getDescription());
        json.put("applyUrl", job.getApplyUrl());
        json.put("salary", job.getSalary());
        json.put("createdAt", job.getCreatedAt());
        json.put("authorId", job.getAuthorId());
        json.put("author", author(author));
        return json;
    }

    public static Map<String, Object> template(Template template, User author) {
        return template(template, author, 0L, false);
    }

    public static Map<String, Object> template(Template template, User author,
                                               long voteCount, boolean viewerHasVoted) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", template.getId());
        json.put("title", template.getTitle());
        json.put("description", template.getDescription());
        json.put("category", template.getCategory());
        json.put("fileUrl", template.getFileUrl());
        json.put("fileName", template.getFileName());
        json.put("fileType", template.getFileType());
        json.put("downloadCount", template.getDownloadCount());
        json.put("voteCount", voteCount);
        json.put("viewerHasVoted", viewerHasVoted);
        json.put("createdAt", template.getCreatedAt());
        json.put("authorId", template.getAuthorId());
        json.put("author", author(author));
        return json;
    }

    public static Map<String, Object> comment(Comment comment, User author) {
        return comment(comment, author, 0L, false);
    }

    public static Map<String, Object> comment(Comment comment, User author, long voteCount, boolean viewerHasVoted) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", comment.getId());
        json.put("body", comment.getBody());
        json.put("imageUrl", comment.getImageUrl());
        json.put("createdAt", comment.getCreatedAt());
        json.put("authorId", comment.getAuthorId());
        json.put("questionId", comment.getQuestionId());
        json.put("parentId", comment.getParentId());
        json.put("author", author(author));
        json.put("voteCount", voteCount);
        json.put("viewerHasVoted", viewerHasVoted);
        return json;
    }
}
