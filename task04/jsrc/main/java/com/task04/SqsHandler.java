package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.ArrayList;
import java.util.List;

@LambdaHandler(
		lambdaName = "sqs_handler",
		roleName = "sqs_handler-role",
		isPublishVersion = false,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SqsTriggerEventSource(
		targetQueue = "async_queue",
		batchSize = 10
)
@DependsOn(
		name = "async_queue",
		resourceType = ResourceType.SQS_QUEUE
)

public class SqsHandler implements RequestHandler<SQSEvent, APIGatewayV2HTTPResponse> {
	public APIGatewayV2HTTPResponse handleRequest(SQSEvent event, Context context) {
		event.getRecords().forEach(message -> context.getLogger().log(message.getBody()));
		String okMessage = "{" + "         \"statusCode\": 200,"
				+ "         \"message\": \"Hello from Lambda\"" + "     }";
		return APIGatewayV2HTTPResponse.builder()
		.withBody(okMessage)
		.withStatusCode(200)
		.build();
	}
}
