package com.docquery.document.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "file_name") 
    private String fileName;
    @Column(name = "file_type")
    private String fileType;
    @Column(name = "file_size")
    private Long fileSize;
    @Column(name = "content_text")
    private String contentText;
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private DocumentStatus status;
    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * 创建上传文档对象
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param fileSize 文件大小
     * @return Document
     */
    public static Document pendingUpload(String fileName, String fileType, Long fileSize) {
        Document document = new Document();
        document.setFileName(fileName);
        document.setFileType(fileType);
        document.setFileSize(fileSize);
        document.setContentText("");
        document.setStatus(DocumentStatus.PENDING);
        return document;
    }
}   
