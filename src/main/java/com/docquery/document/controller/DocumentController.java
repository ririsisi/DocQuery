package com.docquery.document.controller;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.docquery.common.ApiResponse;
import com.docquery.document.model.Document;
import com.docquery.document.model.DocumentStatus;
import com.docquery.document.repository.DocumentRepository;
import com.docquery.storage.FileStorage;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final FileStorage fileStorage;
    private final DocumentRepository documentRepository;

    DocumentController(FileStorage fileStorage, DocumentRepository documentRepository) {
        this.fileStorage = fileStorage;
        this.documentRepository = documentRepository;
    }

    @PostMapping(value = "/upload")
    public ResponseEntity<ApiResponse<Document>> upload(@RequestParam("file") MultipartFile file) {
        try {
            fileStorage.upload(file.getOriginalFilename(),file.getInputStream());
            Document document = new Document();
            document.setFileName(file.getOriginalFilename());
            document.setFileSize(file.getSize());
            document.setFileType(file.getContentType());
            document.setContentText("");
            document.setStatus(DocumentStatus.PENDING);
            documentRepository.save(document);
            return ResponseEntity.ok(ApiResponse.ok(document));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to upload file"));
        }
    }
}
