package com.fit2cloud.fapiao.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// FileUploadRequest.java
@Data
public class FileUploadRequest {
    private String userId;
    private String message = "发票";
}

