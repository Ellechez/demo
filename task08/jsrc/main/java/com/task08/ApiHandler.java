package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.example.WeatherClient;

@LambdaHandler(
		lambdaName = "api_handler",
		roleName = "api_handler-role",
		layers = {"open-meteo"},
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED

)
@LambdaLayer(
		layerName = "open-meteo",
		libraries = {"lib/meteo-1.0-SNAPSHOT.jar"},
		runtime = DeploymentRuntime.JAVA11,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Object, String> {

	public String handleRequest(Object input, Context context) {
		WeatherClient client = new WeatherClient();
		try {
			final JsonNode weatherForecast = client.getWeatherForecast();
			return weatherForecast.toString();
		} catch (Exception e) {
			return e.getMessage();
		}
	}
}