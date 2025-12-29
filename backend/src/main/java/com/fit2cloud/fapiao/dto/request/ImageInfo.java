package com.fit2cloud.fapiao.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageInfo {
    private String name;
    private String url;
    private String file_id;
}
