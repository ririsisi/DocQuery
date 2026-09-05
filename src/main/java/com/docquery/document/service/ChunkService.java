package com.docquery.document.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/** 固定窗口先跑通；重叠 64 是为了少在句子中间切断。结构感知切片以后再换。 */
@Service
public class ChunkService {

    private static final int CHUNK_SIZE = 512;
    private static final int OVERLAP = 64;

    public List<String> chunk(String text) {

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += CHUNK_SIZE - OVERLAP) {
            chunks.add(text.substring(i, Math.min(i + CHUNK_SIZE, text.length())));
        }
        return chunks;

    }
}
