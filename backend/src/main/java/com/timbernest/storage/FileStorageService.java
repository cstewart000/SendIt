package com.timbernest.storage;

import com.timbernest.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private final Path root;

    public FileStorageService(@Value("${sendit.storage-path}") String storagePath) throws IOException {
        this.root = Paths.get(storagePath).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("originals"));
        Files.createDirectories(root.resolve("repaired"));
        Files.createDirectories(root.resolve("gcode"));
        log.info("File storage root={}", root);
    }

    public String storeOriginal(MultipartFile file) {
        String name = UUID.randomUUID() + "_" + safe(file.getOriginalFilename());
        Path dest = root.resolve("originals").resolve(name);
        try {
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored original {}", dest);
            return dest.toString();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    public String writeText(String folder, String filename, String content) {
        Path dest = root.resolve(folder).resolve(filename);
        try {
            Files.writeString(dest, content, StandardCharsets.UTF_8);
            log.info("Wrote text file {}", dest);
            return dest.toString();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write " + filename);
        }
    }

    public Path resolve(String path) {
        return Paths.get(path);
    }

    private String safe(String name) {
        if (name == null) return "upload.bin";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
