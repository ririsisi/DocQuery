package com.docquery.storage;

import java.io.InputStream;

/**
 * 文件存储接口
 */
public interface FileStorage {
    /**
     * 上传文件
     * @param objectKey 对象key
     * @param inputStream 文件流
     */
    void upload(String objectKey, InputStream inputStream);

    /**
     * 加载文件
     * @param objectKey 对象key
     * @return 文件流
     */
    InputStream load(String objectKey);

    /**
     * 删除文件
     * @param objectKey 对象key
     */
    void delete(String objectKey);

}
