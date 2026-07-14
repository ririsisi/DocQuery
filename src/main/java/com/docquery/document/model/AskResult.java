package com.docquery.document.model;

import java.util.List;

public record AskResult(String answer, List<Citation> citations) {

}
