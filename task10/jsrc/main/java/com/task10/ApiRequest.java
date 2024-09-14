package com.task10;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
public class ApiRequest {
    private String method;
    private String path;
    private String authorization_header;
    private Map<String, String> body_json;
}
