package com.fit2cloud.fapiao.controller;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;

/**
 * 将微盘文件字节数组适配为MultipartFile
 */
public class WedriveFileAdapter implements MultipartFile {
    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public WedriveFileAdapter(byte[] content, String filename) {
        this.content = content;
        this.name = "file";
        this.originalFilename = filename;
        // 根据文件名判断contentType
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            this.contentType = "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            this.contentType = "image/png";
        } else if (lowerName.endsWith(".pdf")) {
            this.contentType = "application/pdf";
        } else {
            this.contentType = "application/octet-stream";
        }
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getOriginalFilename() { return originalFilename; }

    @Override
    public String getContentType() { return contentType; }

    @Override
    public boolean isEmpty() { return content == null || content.length == 0; }

    @Override
    public long getSize() { return content.length; }

    @Override
    public byte[] getBytes() throws IOException { return content; }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(content);
        }
    }
}