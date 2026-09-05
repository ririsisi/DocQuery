package com.docquery.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

class GoldensetFileTest {

    @Test
    void loadsFiftySixItemsWithNoisyAndAdversarial() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/eval/goldenset.json")) {
            GoldensetFile file = mapper.readValue(in, GoldensetFile.class);
            assertEquals("v1.4", file.version());
            assertEquals(56, file.items().size());
            assertEquals(22, file.items().stream().filter(i -> "proper_noun".equals(i.type())).count());
            assertEquals(12, file.items().stream().filter(i -> "paraphrase".equals(i.type())).count());
            assertEquals(6, file.items().stream().filter(i -> "negative".equals(i.type())).count());
            assertEquals(8, file.items().stream().filter(i -> "hard".equals(i.type())).count());
            assertEquals(4, file.items().stream().filter(i -> "adversarial".equals(i.type())).count());
            assertEquals(4, file.items().stream().filter(i -> "noisy".equals(i.type())).count());
            assertTrue(file.items().stream().filter(GoldensetItem::expectRefuse)
                    .allMatch(i -> "negative".equals(i.type()) || "adversarial".equals(i.type())));
            assertTrue(file.items().stream().filter(i -> "hard".equals(i.type()) || "noisy".equals(i.type()))
                    .noneMatch(GoldensetItem::expectRefuse));
        }
    }

    @Test
    void scoredNeedlesLiveOnlyInExpectedDoc() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        GoldensetFile file;
        try (InputStream in = getClass().getResourceAsStream("/eval/goldenset.json")) {
            file = mapper.readValue(in, GoldensetFile.class);
        }
        Map<String, String> corpus = new LinkedHashMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:/eval/corpus/*.md")) {
            String name = resource.getFilename();
            if (name == null) {
                continue;
            }
            corpus.put(name, resource.getContentAsString(StandardCharsets.UTF_8));
        }
        assertEquals(17, corpus.size());
        Set<String> scoredTypes = Set.of("hard", "noisy");
        for (GoldensetItem item : file.items()) {
            if (!scoredTypes.contains(item.type())) {
                continue;
            }
            String needle = item.expectedHitContains();
            assertTrue(corpus.get(item.expectedDoc()).contains(needle), item.id() + " missing needle in " + item.expectedDoc());
            long others = corpus.entrySet().stream()
                    .filter(e -> !e.getKey().equals(item.expectedDoc()))
                    .filter(e -> e.getValue().contains(needle))
                    .count();
            assertEquals(0, others, item.id() + " needle leaked into another corpus file");
        }
    }
}
