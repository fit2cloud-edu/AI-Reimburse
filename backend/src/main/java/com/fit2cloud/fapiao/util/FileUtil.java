package com.fit2cloud.fapiao.util;


import com.fit2cloud.fapiao.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class FileUtil {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "pdf", "bmp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 在 FileUtil.java 中简化验证方法
public void validateFile(MultipartFile file) {
    // 移除所有文件检查，只保留基本的null检查
    if (file == null) {
        throw new BusinessException("文件不能为空");
    }
}



    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        return (lastDotIndex == -1) ? "" : filename.substring(lastDotIndex + 1);
    }
}
