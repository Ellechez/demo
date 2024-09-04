package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "table", value = "${target_table}")
}
)

public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final DynamoDB dynamoDB;
	private final Table myTable;


	public ApiHandler() {
		this.dynamoDB = new DynamoDB(new AmazonDynamoDBClient());
		this.myTable = dynamoDB.getTable("cmtr-341056d4-Events-test");
	}

	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		Map<String, Object> response = new HashMap<>();
		try {
			Item item = new Item();
			item.withPrimaryKey("id", UUID.randomUUID().toString());
			item.withInt("principalId", (int) request.get("principalId"));
			item.withString("createdAt", java.time.Instant.now().toString());
			item.withMap("body", (Map<String, String>) request.get("content"));

			myTable.putItem(item);
			response.put("statusCode", HTTP_CREATED);
			response.put("event", item.asMap());

		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			response.put("statusCode", HTTP_INTERNAL_ERROR);
			response.put("error", e.getMessage());
		}
		return response;
	}
}
