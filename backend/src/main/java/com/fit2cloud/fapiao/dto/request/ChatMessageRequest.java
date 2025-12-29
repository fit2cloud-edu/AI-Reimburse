package com.fit2cloud.fapiao.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class ChatMessageRequest {
    private String message;
    private boolean stream = false;
    private boolean re_chat = false;
    private List<ImageInfo> image_list;
    private List<ImageInfo> document_list;

    private String businessContext;//业务上下文-存储报销类型等上下文信息

    public ChatMessageRequest() {
    }

    public ChatMessageRequest(String message, List<ImageInfo> imageList, String businessContext) {
        this.message = message;
        this.image_list = imageList;
        this.businessContext = businessContext;
    }

    public ChatMessageRequest(String message, List<ImageInfo> imageList, List<ImageInfo> documentList, String businessContext) {
        this.message = message;
        this.image_list = imageList;
        this.document_list = documentList;
        this.businessContext = businessContext;
    }
}
