package com.task05;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.UUID;

import static java.net.HttpURLConnection.HTTP_CREATED;

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
@DependsOn(
		name = "Events",
		resourceType = ResourceType.DYNAMODB_TABLE
)
public class ApiHandler implements RequestHandler<Request, Response> {
	private static String DYNAMODB_TABLE = "cmtr-341056d4-Events-test";
	private static Regions REGION = Regions.EU_CENTRAL_1;

	public Response handleRequest(Request request, Context context) {
		//context.getLogger().log("request: " + request.toString());

		Table myTable = getTable();

		Item item = new Item();
		item.withPrimaryKey("id", UUID.randomUUID().toString());
		item.withInt("principalId", request.getPrincipalId());
		item.withString("createdAt", java.time.Instant.now().toString());
		item.withMap("body", request.getContent());
		//context.getLogger().log("item: " + item);


		myTable.putItem(item);
		//context.getLogger().log("putItemOutcome: " + putItemOutcome.toString());

		Response response = new Response();
		response.setStatusCode(HTTP_CREATED);

		Event event = new Event();
		event.setId(item.getString("id"));
		event.setPrincipalId(item.getInt("principalId"));
		event.setCreatedAt(item.getString("createdAt"));
		event.setBody(item.getMap("body"));
		response.setEvent(event);
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