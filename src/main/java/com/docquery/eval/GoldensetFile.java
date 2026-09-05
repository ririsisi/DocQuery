package com.docquery.eval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoldensetFile(String version, String note, List<GoldensetItem> items) {
}
