package com.docquery.document.model;
/**
 * 文档状态枚举类
 */
public enum DocumentStatus {
    /**
     * 待处理
     */
    PENDING,
    /**
     * 处理中
     */
    PROCESSING,
    /**
     * 完成
     */
    COMPLETED,
    /**
     * 失败
     */
    FAILED
}
