package com.docquery.storage;

import java.io.InputStream;

/** 原文件落盘抽象；生产换对象存储只换实现，切片仍进向量库。 */
public interface FileStorage {
    void upload(String objectKey, InputStream inputStream);

    InputStream load(String objectKey);

    void delete(String objectKey);
}
