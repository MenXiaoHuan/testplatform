package com.example.platform.ai.output;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 内容块 —— 模型输出 JSON 中 {@code sections[]} 数组元素的对应 record。
 *
 * <p>支持 6 种块类型，每种类型只填充对应字段：
 * <ul>
 *   <li>{@code heading}   —— 标题：level（1-3）、text</li>
 *   <li>{@code paragraph}  —— 段落：text</li>
 *   <li>{@code list}        —— 列表：items[]、ordered（true=有序）</li>
 *   <li>{@code code}        —— 代码：language、code</li>
 *   <li>{@code quote}       —— 引用：text</li>
 *   <li>{@code table}       —— 表格：headers[]、rows[][]</li>
 * </ul>
 *
 * <p>{@link JsonInclude.Include#NON_NULL} 让序列化时省略 null 字段，减少 token 消耗。
 */
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
