package com.task11;

import java.util.Map;

public record ApiRequest(String method, String path, String authorization_header, Map<String, String> body_json) {

}
