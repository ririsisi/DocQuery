package com.docquery.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

@Service
public class TxtParser implements DocumentParser {

    @Override
    public String parse(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        } catch (IOException e) {
            throw new RuntimeException("文本解析失败", e);
        }
    }
}
