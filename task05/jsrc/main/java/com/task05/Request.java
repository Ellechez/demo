package com.task05;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@Data
@NoArgsConstructor
public class Request {
    private int principalId;
    private Map<String, String> content;

    @Override
    public String toString() {
        return "Request{" +
                "principalId=" + principalId +
                ", content=" + content +
                '}';
    }
}
