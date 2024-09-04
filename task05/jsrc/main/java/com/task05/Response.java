package com.task05;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Response {
    private int statusCode;
    private Object event;

    @Override
    public String toString() {
        return "Response{" +
                "statusCode=" + statusCode +
                ", event=" + event +
                '}';
    }
}
