package com.task09;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.EventSourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	tracingMode= TracingMode.Active
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EventSource(eventType = EventSourceType.DYNAMODB_TRIGGER)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "table", value = "${target_table}")
}
)
public class Processor implements RequestHandler<Object, Map<String, Object>> {
	private final String FORECAST_URI = "https://api.open-meteo.com/v1/forecast?latitude=41.2647&longitude=69.2163&hourly=temperature_2m";
	private HttpRequest request = null;
	private HttpResponse<String> response = null;
	private static String DYNAMODB_TABLE = "cmtr-341056d4-Weather-test";
	private static Regions REGION = Regions.EU_CENTRAL_1;
	public Map<String, Object> handleRequest(Object request, Context context) {

		HttpResponse<String> forecast = getForecast();
		addRecordToDynamoDbTable(forecast.body());

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("statusCode", forecast.statusCode());
		resultMap.put("body", forecast.body());
		return resultMap;
	}

	private void addRecordToDynamoDbTable(String forecastBody) {
		Item item = new Item();
		item.withString("id", UUID.randomUUID().toString());
		item.withJSON("forecast", forecastBody);

		try {
			getTable().putItem(item);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}


	private HttpResponse<String> getForecast() {
		try {
			request = HttpRequest.newBuilder().uri(new URI(FORECAST_URI)).GET().build();
			response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return response;
	}
	private Table getTable() {
		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
				.withRegion(REGION)
				.build();
		DynamoDB dynamoDB = new DynamoDB(client);
		return dynamoDB.getTable(DYNAMODB_TABLE);
	}


}
