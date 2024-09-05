package com.task06;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.EventSourceType;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 1
)
@DependsOn(
		name = "Configuration",
		resourceType = ResourceType.DYNAMODB_TABLE
)
@EventSource(eventType = EventSourceType.DYNAMODB_TRIGGER)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "table", value = "${target_table}")
}
)

public class AuditProducer implements RequestHandler<DynamodbEvent, String> {
	private static String DYNAMODB_TABLE = "cmtr-341056d4-Audit-test";
	private static Regions REGION = Regions.EU_CENTRAL_1;

	@Override
	public String handleRequest(DynamodbEvent event, Context context) {
		context.getLogger().log("Lambda trigger active");
		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
			if (record.getEventName().equals("INSERT") || record.getEventName().equals("MODIFY")) {

				Map<String, AttributeValue> newImageMap = record.getDynamodb().getNewImage();
				context.getLogger().log("new image = " + newImageMap);
				Map<String, AttributeValue> oldImageMap = record.getDynamodb().getOldImage();
				context.getLogger().log("old image = " + newImageMap);

				Item item = new Item();
				item.withString("id", UUID.randomUUID().toString());
				item.withString("itemKey", newImageMap.get("key").getS());
				item.withString("modificationTime", Instant.now().toString());

				if (record.getEventName().equals("INSERT")) {
					item.withMap("newValue", convert(newImageMap));
				} else if (record.getEventName().equals("MODIFY")) {
					item.withString("updatedAttribute", "value");
					item.withNumber("oldValue", Integer.parseInt(oldImageMap.get("value").getN()));
					item.withNumber("newValue", Integer.parseInt(newImageMap.get("value").getN()));
				}

				Table auditTable = getTable();
				auditTable.putItem(item);
			}
		}
		return "ok";
	}

	private Map<String, Object> convert(Map<String, AttributeValue> image) {
		Map<String, Object> stringObjectMap = new HashMap<>();
		for (Map.Entry<String, AttributeValue> entry : image.entrySet()) {
			AttributeValue value = entry.getValue();
			if (value.getS() != null) {
				stringObjectMap.put(entry.getKey(), value.getS());
			} else if (value.getN() != null) {
				stringObjectMap.put(entry.getKey(), Integer.parseInt(value.getN()));
			}
		}
		return stringObjectMap;
	}

	private Table getTable() {
		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
				.withRegion(REGION)
				.build();
		DynamoDB dynamoDB = new DynamoDB(client);
		return dynamoDB.getTable(DYNAMODB_TABLE);
	}
}
