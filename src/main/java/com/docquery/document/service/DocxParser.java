package com.docquery.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
@Service
public class DocxParser implements DocumentParser{

    @Override
    public String parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            return document.getParagraphs().stream().map(paragraph -> paragraph.getText()).collect(Collectors.joining("\n")).replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (IOException exception) {
            throw new RuntimeException("DOCX 文档解析失败", exception);
        }
        
    }
}
