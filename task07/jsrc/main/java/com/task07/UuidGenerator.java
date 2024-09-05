package com.task07;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.EventSourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.util.*;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EventSource(
		eventType = EventSourceType.CLOUDWATCH_RULE_TRIGGER
)
@RuleEventSource(targetRule = "uuid_trigger")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
}
)
public class UuidGenerator implements RequestHandler<Object, String> {
	private final AmazonS3 s3Client = AmazonS3ClientBuilder
			.standard()
			.withRegion(Regions.EU_CENTRAL_1)
			.build();
	private final ObjectMapper objectMapper = new ObjectMapper();
	public String handleRequest(Object input, Context context) {
		List<UUID> list = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			list.add(UUID.randomUUID());
		}

		Map<String, Object> objectMap = new HashMap<>();
		objectMap.put("ids", list);

		try {
			s3Client.putObject("cmtr-341056d4-uuid-storage-test",
					Instant.now().toString(),
					objectMapper.writeValueAsString(objectMap));
			return "ok";
		} catch (Exception e) {
			context.getLogger().log(e.getMessage());
			return e.getMessage();
		}
	}
}
