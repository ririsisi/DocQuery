package com.docquery.document.service;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ParserFactory {
    
    private final Map<String, DocumentParser> parserMap;
    
    public ParserFactory (Map<String, DocumentParser> parserMap) {
        this.parserMap = parserMap;
    }

    public DocumentParser getParser(String fileName,String fileType) {
        String suffix =  fileType.contains("/") ? fileType.substring(fileType.lastIndexOf("/") + 1) : fileType;
        String beanName = suffix + "Parser";
        if (parserMap.containsKey(beanName)) {
            return parserMap.get(beanName);
        } else if (fileName != null && fileName.contains(".")) {
            suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
            beanName = suffix + "Parser";
            if (parserMap.containsKey(beanName)) {
                return parserMap.get(beanName);
            } else {
                throw new IllegalArgumentException("不支持的文件类型: " + suffix);
            }
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + fileType);
        }
    }
}
