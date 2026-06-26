package com.docquery.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docquery.document.model.Document;

/**
 * 文档仓库接口
 * @author ririsisi
 * @version 1.0
 * @since 2026-06-26
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

}
