package com.docquery.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;

@Service
public class LocalFileStorage implements FileStorage {

    private final String basePath = "./data/files/";

    public LocalFileStorage() {
        try {
            Files.createDirectories(Paths.get(basePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory: " + basePath, e);
        }
    }

    private Path resolvePath(String objectKey) {
        return Paths.get(basePath + objectKey);
    }
    
    @Override
    public void upload(String objectKey, InputStream inputStream) {
        try {
            Path filePath = resolvePath(objectKey);
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(inputStream, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + objectKey, e);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        try {
            Path filePath = resolvePath(objectKey);
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load file: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Path filePath = resolvePath(objectKey);
            Files.delete(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + objectKey, e);
        }
    }

}
