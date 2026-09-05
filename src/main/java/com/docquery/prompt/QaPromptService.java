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

import com.docquery.document.model.EvidenceLevel;
import com.docquery.document.model.EvidenceWindow;

/**
 * Prompt 当配置读，改口吻升版本文件而不是改 Java。
 * 未接线的模板启动时也校验存在，避免目录残缺被说成「已经上了」。
 */
@Service
public class QaPromptService {

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
    private final String chatSystemTemplate;
    private final String userTemplate;
    private final String contextTemplate;
    private final String refuseNone;
    private final String guidanceNone;
    private final String guidanceWeak;
    private final String guidancePartial;
    private final String guidanceSufficient;

    public QaPromptService(@Value("${docquery.prompt.version:v1}") String version) {
        String base = "prompts/" + version + "/";
        for (String file : REQUIRED_FILES) {
            read(base + file);
        }
        this.systemTemplate = read(base + "qa-system.md");
        this.chatSystemTemplate = read(base + "assistant-chat.md");
        this.userTemplate = read(base + "qa-user.md");
        this.contextTemplate = read(base + "qa-context.md");
        this.refuseNone = read(base + "qa-refuse-none.md").trim();
        this.guidanceNone = read(base + "qa-guidance-none.md").trim();
        this.guidanceWeak = read(base + "qa-guidance-weak.md").trim();
        this.guidancePartial = read(base + "qa-guidance-partial.md").trim();
        this.guidanceSufficient = read(base + "qa-guidance-sufficient.md").trim();
    }

    public String system() {
        return systemTemplate;
    }

    public String chatSystem() {
        return chatSystemTemplate;
    }

    public String refuseWhenNoEvidence() {
        return refuseNone;
    }

    public String guidance(EvidenceLevel level) {
        if (level == null) {
            return guidanceWeak;
        }
        return switch (level) {
            case NONE -> guidanceNone;
            case WEAK -> guidanceWeak;
            case PARTIAL -> guidancePartial;
            case SUFFICIENT -> guidanceSufficient;
        };
    }

    public String levelLabel(EvidenceLevel level) {
        return level == null ? "WEAK" : level.name();
    }

    public String renderUser(String question, List<EvidenceWindow> windows, String evidenceLevel,
            String evidenceGuidance) {
        String evidence = formatEvidence(windows);
        String context = fill(contextTemplate, Map.of("evidence", evidence));
        return fill(userTemplate, Map.of(
                "question", nullToEmpty(question),
                "evidenceLevel", nullToEmpty(evidenceLevel),
                "evidenceGuidance", nullToEmpty(evidenceGuidance),
                "context", context));
    }

    public String formatEvidence(List<EvidenceWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return "（无）";
        }
        return windows.stream().map(window -> {
            StringBuilder line = new StringBuilder();
            line.append("[E").append(window.primaryChunkId()).append("]");
            line.append(" documentId=").append(window.documentId());
            line.append(" chunkIndex=").append(window.startChunkIndex());
            if (window.endChunkIndex() != window.startChunkIndex()) {
                line.append('-').append(window.endChunkIndex());
            }
            line.append('\n');
            line.append(nullToEmpty(window.content()));
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
