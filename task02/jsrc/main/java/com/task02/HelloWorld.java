package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;



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
public class HelloWorld implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    public APIGatewayV2HTTPResponse  handleRequest(APIGatewayV2HTTPEvent request, Context context) {

        context.getLogger().log("request:" + request.toString());
        String httpMethod = request.getRequestContext().getHttp().getMethod();
        String rawPath = request.getRawPath();
        context.getLogger().log("path:" + rawPath);

        String okMessage = "{" + "         \"statusCode\": 200,"
                + "         \"message\": \"Hello from Lambda\"" + "     }";
        String errorMessage = String.format("{\n" + "   \"statusCode\":400,\n"
                + "   \"message\":\"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"\n"
                + "}", rawPath, httpMethod);

        if (rawPath.equals("/hello")) {
            return APIGatewayV2HTTPResponse .builder()
                    .withBody(okMessage)
                    .withStatusCode(200)
                    .build();
        } else {
            return APIGatewayV2HTTPResponse.builder()
                    .withBody(errorMessage)
                    .withStatusCode(400)
                    .build();
        }
    }
}
