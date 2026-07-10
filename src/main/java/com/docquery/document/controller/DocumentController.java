package com.docquery.document.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docquery.common.ApiResponse;
import com.docquery.document.model.Document;
import com.docquery.document.model.DocumentChunk;
import com.docquery.document.model.DocumentStatus;
import com.docquery.document.repository.DocumentChunkRepository;
import com.docquery.document.repository.DocumentRepository;
import com.docquery.document.service.ChunkService;
import com.docquery.document.service.DocumentParser;
import com.docquery.document.service.ParserFactory;
import com.docquery.storage.FileStorage;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentChunkRepository documentChunkRepository;
    private final FileStorage fileStorage;
    private final DocumentRepository documentRepository;
    private final ParserFactory parserFactory;
    private final ChunkService chunkService;

    DocumentController(FileStorage fileStorage, DocumentRepository documentRepository, ParserFactory parserFactory,
            ChunkService chunkService, DocumentChunkRepository documentChunkRepository) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
        this.parserFactory = parserFactory;
        this.chunkService = chunkService;
        this.documentChunkRepository = documentChunkRepository;
    }

    /**
     * 上传文档
     * 
     * @param file 文件
     * @return 文档对象
     */
    @PostMapping(value = "/upload")
    public ResponseEntity<ApiResponse<Document>> upload(@RequestParam("file") MultipartFile file) {

        try {

            Document document = Document.pendingUpload(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize());

            // 把文件上传到本地
            fileStorage.upload(file.getOriginalFilename(), file.getInputStream());

            // 获取文档解析器
            DocumentParser documentParser = parserFactory.getParser(file.getOriginalFilename(), file.getContentType());

            try (InputStream in = fileStorage.load(file.getOriginalFilename())) {
                String contentText = documentParser.parse(in);
                document.setContentText(contentText);
                document.setStatus(DocumentStatus.COMPLETED);
                // 保存文档对象
                Document savedDocument = documentRepository.save(document);
                List<String> chunks = chunkService.chunk(contentText);
                int index = 0;
                for (String chunk : chunks) {
                    DocumentChunk documentChunk = new DocumentChunk();
                    documentChunk.setDocumentId(savedDocument.getId());
                    documentChunk.setChunkIndex(index++);
                    documentChunk.setContent(chunk);
                    documentChunk.setCreatedAt(LocalDateTime.now());
                    documentChunkRepository.save(documentChunk);
                }
            }

            return ResponseEntity.ok(ApiResponse.ok(document));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to upload file"));
        }
    }
}
