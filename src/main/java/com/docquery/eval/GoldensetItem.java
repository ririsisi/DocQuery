package com.docquery.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldensetItem(
        String id,
        String type,
        String question,
        @JsonProperty("expected_doc") String expectedDoc,
        @JsonProperty("expected_hit_contains") String expectedHitContains,
        @JsonProperty("answer_points") String answerPoints,
        @JsonProperty("expect_refuse") boolean expectRefuse) {
}
