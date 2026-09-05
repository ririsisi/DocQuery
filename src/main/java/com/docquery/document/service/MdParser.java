package com.docquery.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

/** 评测语料是 UTF-8 Markdown；统一换行以免标针对不齐。 */
@Service
public class MdParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        } catch (IOException e) {
            throw new RuntimeException("Markdown 文档解析失败", e);
        }
    }
}
