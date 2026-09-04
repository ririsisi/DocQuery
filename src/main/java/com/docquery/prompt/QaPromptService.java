package com.docquery.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.docquery.document.model.DocumentChunk;

/**
 * 从 classpath 读取版本化 Prompt，业务代码不写死系统提示词。
 * 终面模板一次写齐；未接线的文件启动时仍校验存在，避免目录残缺。
 */
@Service
public class QaPromptService {

    public static final String LEVEL_UNGATED = "未分级";

    private static final List<String> REQUIRED_FILES = List.of(
            "qa-system.md",
            "qa-user.md",
            "qa-context.md",
            "qa-refuse-none.md",
            "qa-guidance-ungated.md",
            "qa-guidance-none.md",
            "qa-guidance-weak.md",
            "qa-guidance-partial.md",
            "qa-guidance-sufficient.md",
            "query-planning-system.md",
            "query-planning-user.md",
            "assistant-chat.md",
            "assistant-kb-search.md");

    private final String systemTemplate;
    private final String userTemplate;
    private final String contextTemplate;
    private final String refuseNone;
    private final String guidanceUngated;

    public QaPromptService(@Value("${docquery.prompt.version:v1}") String version) {
        String base = "prompts/" + version + "/";
        for (String file : REQUIRED_FILES) {
            read(base + file);
        }
        this.systemTemplate = read(base + "qa-system.md");
        this.userTemplate = read(base + "qa-user.md");
        this.contextTemplate = read(base + "qa-context.md");
        this.refuseNone = read(base + "qa-refuse-none.md").trim();
        this.guidanceUngated = read(base + "qa-guidance-ungated.md").trim();
    }

    public String system() {
        return systemTemplate;
    }

    public String refuseWhenNoEvidence() {
        return refuseNone;
    }

    public String ungatedGuidance() {
        return guidanceUngated;
    }

    public String renderUser(String question, List<DocumentChunk> chunks, String evidenceLevel,
            String evidenceGuidance) {
        String evidence = formatEvidence(chunks);
        String context = fill(contextTemplate, Map.of("evidence", evidence));
        return fill(userTemplate, Map.of(
                "question", nullToEmpty(question),
                "evidenceLevel", nullToEmpty(evidenceLevel),
                "evidenceGuidance", nullToEmpty(evidenceGuidance),
                "context", context));
    }

    public String formatEvidence(List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "（无）";
        }
        return chunks.stream().map(chunk -> {
            StringBuilder line = new StringBuilder();
            line.append("[E").append(chunk.getId()).append("]");
            line.append(" documentId=").append(chunk.getDocumentId());
            line.append(" chunkIndex=").append(chunk.getChunkIndex());
            line.append('\n');
            line.append(nullToEmpty(chunk.getContent()));
            return line.toString();
        }).collect(Collectors.joining("\n\n"));
    }

    private static String fill(String template, Map<String, String> vars) {
        String out = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String read(String classpath) {
        ClassPathResource resource = new ClassPathResource(classpath);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取提示词: " + classpath, e);
        }
    }
}
