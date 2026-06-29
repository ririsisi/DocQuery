package com.docquery.document.service;

import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
@Service
public class PdfParser implements DocumentParser{

    @Override
    public String parse(InputStream inputStream) {
        try (PDDocument pdfDocument = PDDocument.load(inputStream)) {
            String text = new PDFTextStripper().getText(pdfDocument);
            return text.replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (IOException exception) {
            throw new RuntimeException("PDF 文档解析失败", exception);
        }
    }
}
