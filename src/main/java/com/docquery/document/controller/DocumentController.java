package com.docquery.document.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;

import com.docquery.common.ApiResponse;
import com.docquery.document.model.AskMode;
import com.docquery.document.model.AskResult;
import com.docquery.document.model.Document;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.DocumentStatus;
import com.docquery.document.repository.DocumentChunkRepository;
import com.docquery.document.repository.DocumentRepository;
import com.docquery.document.service.AskService;
import com.docquery.document.service.ChunkService;
import com.docquery.document.service.DocumentParser;
import com.docquery.document.service.EmbeddingService;
import com.docquery.document.service.ParserFactory;
import com.docquery.storage.FileStorage;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final FileStorage fileStorage;
    private final DocumentRepository documentRepository;
    private final ParserFactory parserFactory;
    private final ChunkService chunkService;
    private final AskService askService;
    private final long sseTimeoutMs;

    DocumentController(FileStorage fileStorage, DocumentRepository documentRepository, ParserFactory parserFactory,
            ChunkService chunkService, DocumentChunkRepository documentChunkRepository,
            EmbeddingService embeddingService, AskService askService,
            @Value("${docquery.sse.timeout-ms:60000}") long sseTimeoutMs) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
        this.parserFactory = parserFactory;
        this.chunkService = chunkService;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.askService = askService;
        this.sseTimeoutMs = sseTimeoutMs;
    }

    @PostMapping(value = "/upload")
    @Transactional
    public ResponseEntity<ApiResponse<Document>> upload(@RequestParam("file") MultipartFile file) {

        try {

            Document document = Document.pendingUpload(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize());

            fileStorage.upload(file.getOriginalFilename(), file.getInputStream());

            DocumentParser documentParser = parserFactory.getParser(file.getOriginalFilename(), file.getContentType());

            try (InputStream in = fileStorage.load(file.getOriginalFilename())) {
                String contentText = documentParser.parse(in);
                document.setContentText(contentText);
                document.setStatus(DocumentStatus.COMPLETED);
                Document savedDocument = documentRepository.save(document);
                List<String> chunks = chunkService.chunk(contentText);
                int index = 0;
                for (String chunk : chunks) {
                    DocumentChunk documentChunk = new DocumentChunk();
                    documentChunk.setDocumentId(savedDocument.getId());
                    documentChunk.setChunkIndex(index++);
                    documentChunk.setContent(chunk);
                    documentChunk.setCreatedAt(LocalDateTime.now());
                    Long chunkId = documentChunkRepository.save(documentChunk).getId();
                    // pgvector 列不能走普通字段赋值，必须 CAST 成 vector
                    float[] embedding = embeddingService.embed(chunk);
                    documentChunkRepository.updateEmbeddings(chunkId, toVectorString(embedding));
                }
            }

            return ResponseEntity.ok(ApiResponse.ok(document));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to upload file"));
        }
    }

    @PostMapping(value = "/ask")
    public ResponseEntity<ApiResponse<AskResult>> ask(
            @RequestParam("question") String question,
            @RequestParam(value = "mode", defaultValue = "KB") String mode) {
        try {
            AskResult askResult = askService.ask(question, AskMode.fromParam(mode));
            return ResponseEntity.ok(ApiResponse.ok(askResult));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
        }
    }

    /**
     * 流式问答：token / citations / done / error，注释心跳防网关掐连接。
     * mode=CHAT 不检索；默认 KB。
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            @RequestParam("question") String question,
            @RequestParam(value = "mode", defaultValue = "KB") String mode,
            HttpServletResponse response) {
        AskMode askMode = AskMode.fromParam(mode);
        response.setHeader("Cache-Control", "no-cache");
        // 关掉反向代理缓冲，否则 SSE 会攒一批才推到浏览器
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        askService.askStream(question, askMode, emitter);
        return emitter;
    }

    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
