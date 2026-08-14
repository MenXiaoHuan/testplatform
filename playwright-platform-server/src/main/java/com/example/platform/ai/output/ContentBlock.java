package com.example.platform.ai.output;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlock(
        String type,
        Integer level,
        String text,
        List<String> items,
        Boolean ordered,
        String language,
        String code,
        List<String> headers,
        List<List<String>> rows
) {
    public ContentBlock {
        if (type == null) type = "paragraph";
    }

    public static ContentBlock heading(int level, String text) {
        return new ContentBlock("heading", level, text, null, null, null, null, null, null);
    }

    public static ContentBlock paragraph(String text) {
        return new ContentBlock("paragraph", null, text, null, null, null, null, null, null);
    }

    public static ContentBlock list(List<String> items, boolean ordered) {
        return new ContentBlock("list", null, null, items, ordered, null, null, null, null);
    }

    public static ContentBlock code(String language, String code) {
        return new ContentBlock("code", null, null, null, null, language, code, null, null);
    }

    public static ContentBlock quote(String text) {
        return new ContentBlock("quote", null, text, null, null, null, null, null, null);
    }

    public static ContentBlock table(List<String> headers, List<List<String>> rows) {
        return new ContentBlock("table", null, null, null, null, null, null, headers, rows);
    }
}
