package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.epam.openapi.OpenAPISDK;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.Map;
import java.util.function.Function;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		isPublishVersion = false,
		layers = "open-meteo",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED

)
@LambdaLayer(
		layerName = "open-meteo",
		libraries = "lib/open-meteo-1.0.jar",
		runtime = DeploymentRuntime.JAVA11,
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)

public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, String> {
	private OpenAPISDK openapi = new OpenAPISDK();
	private Key key;
	private final Map<Key, String> routeHandlers =
			Map.of(new Key("GET", "/weather"), openapi.getForecast());

	@Override
	public String handleRequest(APIGatewayV2HTTPEvent requestEvent, Context context) {
		key = new Key(requestEvent.getRequestContext().getHttp().getMethod(),
				requestEvent.getRequestContext().getHttp().getPath());
		return this.routeHandlers.getOrDefault(key, badResponse());
	}

	private String badResponse() {
		return "{" +
				"\"statusCode\": 404," +
				"\"message\": \"Bad request syntax or unsupported method\"" +
				"}";
	}
}