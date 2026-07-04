package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
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
        json.put("openToMentor", user.isOpenToMentor());
        json.put("seekingMentor", user.isSeekingMentor());
        json.put("createdAt", user.getCreatedAt());
        return json;
    }

    /** Self view — includes contact details. */
    public static Map<String, Object> privateUser(User user) {
        Map<String, Object> json = publicUser(user);
        json.put("email", user.getEmail());
        json.put("phone", user.getPhone());
        json.put("isBanned", user.isBanned());
        return json;
    }

    public static Map<String, Object> comment(Comment comment, User author) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", comment.getId());
        json.put("body", comment.getBody());
        json.put("imageUrl", comment.getImageUrl());
        json.put("createdAt", comment.getCreatedAt());
        json.put("authorId", comment.getAuthorId());
        json.put("questionId", comment.getQuestionId());
        json.put("author", author(author));
        return json;
    }
}
