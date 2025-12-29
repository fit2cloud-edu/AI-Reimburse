package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ChatMessageResponse {
    private String code;
    private String message;
    private ChatMessageData data;

    @Data
    public static class ChatMessageData {
        private String chat_id;
        private String id;
        private Boolean operate;
        private String content;
        private Boolean is_end;
        private Integer completion_tokens;
        private Integer prompt_tokens;
        private List<Answer> answer_list;
    }

    @Data
    public static class Answer {
        private String view_type;
        private String content;
        private String runtime_node_id;
        private String chat_record_id;
        private Object child_node;
        private String reasoning_content;
        private String real_node_id;
    }
}
