package com.docquery.document.service;

import java.io.InputStream;

public interface DocumentParser {
    String parse(InputStream inputStream);
}
