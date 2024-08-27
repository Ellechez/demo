package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "hello_world",
        roleName = "hello_world-role",
        isPublishVersion = false,
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
        Map<String, Object> resultMap = new HashMap<>();
        String endpoint = "/hello";

        Map<String,Map<String, Object>> requestContext =
                (Map<String, Map<String, Object>>) request.get("requestContext");
        Map<String, Object> url = requestContext.get("http");

        String rawPath = url.get("rawPath").toString();
        String errorMessage = "Bad request syntax or unsupported method. Request path: "
                                    + rawPath
                                    + ". HTTP method: "
                                    + url.get("method").toString();

        if (rawPath.equals(endpoint)) {
            resultMap.put("statusCode", 200);
            resultMap.put("message", "Hello from Lambda");
        }
        else {
            resultMap.put("statusCode", 400);
            resultMap.put("message", errorMessage);
        }

        return resultMap;
    }
}
