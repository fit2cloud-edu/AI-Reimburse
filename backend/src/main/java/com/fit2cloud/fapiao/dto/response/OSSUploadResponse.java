package com.fit2cloud.fapiao.dto.response;

import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

@Data
public class OSSUploadResponse {
    private String code;
    private String message;

    @JsonDeserialize(using = OSSUploadDataDeserializer.class)
    private OSSUploadData data;

    @Data
    public static class OSSUploadData {
        private String file_id;
        private String file_name;
        private String url;
        private String filePath; // 用于存储直接返回的文件路径字符串

        // 添加一个统一获取文件标识的方法
        public String getFileIdentifier() {
            if (file_id != null && !file_id.isEmpty()) {
                return file_id;
            }
            return filePath;
        }
    }

    // 自定义反序列化器处理不同的data格式
    public static class OSSUploadDataDeserializer extends JsonDeserializer<OSSUploadData> {
        @Override
        public OSSUploadData deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = p.getCodec().readTree(p);

            OSSUploadData data = new OSSUploadData();

            if (node.isTextual()) {
                // 处理字符串格式的响应 "./oss/file/019a47bf-dd31-7ea0-a25d-0ca75cc9feea"
                data.setFilePath(node.asText());
            } else if (node.isObject()) {
                // 处理对象格式的响应 { "file_id": "...", "file_name": "...", "url": "..." }
                if (node.has("file_id")) {
                    data.setFile_id(node.get("file_id").asText());
                }
                if (node.has("file_name")) {
                    data.setFile_name(node.get("file_name").asText());
                }
                if (node.has("url")) {
                    data.setUrl(node.get("url").asText());
                }
            }

            return data;
        }
    }
}
