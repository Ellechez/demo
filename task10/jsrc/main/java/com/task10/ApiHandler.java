package com.task10;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;


import java.util.*;

@LambdaHandler(lambdaName = "api_handler",
		roleName = "api_handler-role",
		runtime = DeploymentRuntime.JAVA17,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "region", value = "${region}"),
		@EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
		@EnvironmentVariable(key = "reservations_table", value = "${reservations_table}"),
		@EnvironmentVariable(key = "booking_userpool", value = "${booking_userpool}")})
public class ApiHandler implements RequestHandler<ApiRequest, APIGatewayV2HTTPResponse> {

	private final ObjectMapper objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
	private final AmazonDynamoDB amazonDynamoDB = getClient();
	private final CognitoIdentityProviderClient identityProviderClient = CognitoIdentityProviderClient.builder().region(Region.of(System.getenv("region"))).build();
	private static Regions REGION = Regions.EU_CENTRAL_1;
	@Override
	public APIGatewayV2HTTPResponse handleRequest(ApiRequest requestEvent, Context context) {
		System.out.println("API request:" + requestEvent);
		 switch(requestEvent.getPath()) {
			case "/signup" :
				return signUpUser(requestEvent, getUserPoolId());
			case "/signin" :
				return signInUser(requestEvent, getUserPoolId(), createAppClient(getUserPoolId()));
			case "/tables" :
				if(requestEvent.getMethod().equals("POST")) {
					return persistTable(new Table(requestEvent));
				} else {
					return scanTable();
				}
			case "/reservations" :
				if(requestEvent.getMethod().equals("POST")) {
					return persistReservation(new Reservation(requestEvent));
				} else {
					return scanReservations();
				}
			default :
				context.getLogger().log("Processing" +  requestEvent.getAuthorization_header());
				return findTable(requestEvent.getAuthorization_header());
		}
	}


	private String createAppClient(String userPoolId) {
		var result = identityProviderClient.createUserPoolClient(CreateUserPoolClientRequest.builder()
				.userPoolId(userPoolId).explicitAuthFlows(
						ExplicitAuthFlowsType.ALLOW_ADMIN_USER_PASSWORD_AUTH, ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH)
				.clientName("api_client").build());

		return result.userPoolClient().clientId();
	}

	private APIGatewayV2HTTPResponse signUpUser(ApiRequest apiRequest, String userPoolId) {
		try {
			var userAttributeList = new ArrayList<AttributeType>();
			userAttributeList.add(AttributeType.builder().name("email").value(apiRequest.getBody_json().get("email")).build());
			var adminCreateUserRequest = AdminCreateUserRequest.builder()
					.temporaryPassword(apiRequest.getBody_json().get("password"))
					.userPoolId(userPoolId)
					.username(apiRequest.getBody_json().get("email"))
					.messageAction(MessageActionType.SUPPRESS)
					.userAttributes(userAttributeList).build();
			identityProviderClient.adminCreateUser(adminCreateUserRequest);

			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).build();
		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while signing up user " + e.awsErrorDetails().errorMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse signInUser(ApiRequest apiRequest, String userPoolId, String clientId) {
		var authRequest = AdminInitiateAuthRequest.builder()
				.authFlow("ADMIN_USER_PASSWORD_AUTH")
				.authParameters(Map.of(
						"USERNAME", apiRequest.getBody_json().get("email"),
						"PASSWORD", apiRequest.getBody_json().get("password")
				))
				.userPoolId(userPoolId)
				.clientId(clientId)
				.build();

		try {
			var authResponse = identityProviderClient.adminInitiateAuth(authRequest);
			var authResult = authResponse.authenticationResult();
			if(Objects.nonNull(authResponse.challengeName()) && authResponse.challengeName().equals(ChallengeNameType.NEW_PASSWORD_REQUIRED)) {
				var adminRespondToAuthChallengeResponse = identityProviderClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
						.userPoolId(userPoolId)
						.clientId(clientId)
						.session(authResponse.session())
						.challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
						.challengeResponses(
								Map.of("NEW_PASSWORD", apiRequest.getBody_json().get("password"),
										"USERNAME", apiRequest.getBody_json().get("email"))).build());
				authResult = adminRespondToAuthChallengeResponse.authenticationResult();
			}

			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(authResult.idToken()).build();
		} catch (Exception e) {
			System.err.println("Error while signing in user " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private String getUserPoolId() {
		var userPoolDescriptionType = UserPoolDescriptionType.builder().id("test-id").build();
		try {
			var request = ListUserPoolsRequest.builder().maxResults(50).build();
			var response = identityProviderClient.listUserPools(request);
			userPoolDescriptionType = response.userPools().stream().filter(value -> value.name().equals(System.getenv("booking_userpool")))
					.findFirst().orElse(userPoolDescriptionType);
			return userPoolDescriptionType.id();

		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while listing the user pools: " + e.awsErrorDetails().errorMessage());
		}
		return userPoolDescriptionType.id();
	}

	private APIGatewayV2HTTPResponse persistTable(Table table) {
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(table.getId())));
			attributesMap.put("number", new AttributeValue().withN(String.valueOf(table.getNumber())));
			attributesMap.put("places", new AttributeValue().withN(String.valueOf(table.getPlaces())));
			attributesMap.put("isVip", new AttributeValue().withBOOL(table.getIsVip()));
			if (Objects.nonNull(table.getMinOrder())) {
				attributesMap.put("minOrder", new AttributeValue().withN(String.valueOf(table.getMinOrder())));
			}
			amazonDynamoDB.putItem(System.getenv("tables_table"), attributesMap);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(String.valueOf(table.getId())).build();
		} catch(Exception e) {
			System.err.println("Error while persisting table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanTable() {
		try {
			var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
					.getItems().stream().map(x -> new Table(x)).toList();
			var apiResponse = new TableResponse(tableList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse findTable(String tableId) {
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(tableId)));
			var result = amazonDynamoDB.getItem(System.getenv("tables_table"), attributesMap).getItem();
			var tableResult = new Table(result);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(tableResult)).build();
		} catch (Exception e) {
			System.err.println("Error while finding table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanReservations() {
		try {
			var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
					.getItems().stream().map(x -> new Reservation(x)).toList();
			var apiResponse = new ReservationResponse(reservationList);

			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse persistReservation(Reservation reservation) {
		try {
			if(validateTable(reservation) && validateReservation(reservation)) {
				var attributesMap = new HashMap<String, AttributeValue>();
				attributesMap.put("id", new AttributeValue(UUID.randomUUID().toString()));
				attributesMap.put("tableNumber", new AttributeValue().withN(String.valueOf(reservation.getTableNumber())));
				attributesMap.put("clientName", new AttributeValue(String.valueOf(reservation.getClientName())));
				attributesMap.put("phoneNumber", new AttributeValue(String.valueOf(reservation.getPhoneNumber())));
				attributesMap.put("date", new AttributeValue(reservation.getDate()));
				attributesMap.put("slotTimeStart", new AttributeValue(String.valueOf(reservation.getSlotTimeStart())));
				attributesMap.put("slotTimeEnd", new AttributeValue(String.valueOf(reservation.getSlotTimeEnd())));
				amazonDynamoDB.putItem(System.getenv("reservations_table"), attributesMap);
				return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withBody(UUID.randomUUID().toString()).build();
			} else {
				return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR, there is already a reservation or the table does not exist").build();
			}
		} catch(Exception e) {
			System.err.println("Error while persisting reservation " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private boolean validateTable(Reservation reservation) {
		var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
				.getItems().stream().map(x -> new Table(x)).filter(value -> reservation.getTableNumber().equals(value.getNumber())).count();
		return tableList == 1;
	}

	private boolean validateReservation(Reservation reservation) {
		var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
				.getItems().stream().map(x -> new Reservation(x))
				.filter(value ->
						value.getTableNumber().equals(reservation.getTableNumber()) && value.getSlotTimeStart().equals(reservation.getSlotTimeStart())
								&& value.getSlotTimeEnd().equals(reservation.getSlotTimeEnd())).count();
		System.out.println("Validate reservation:" + reservationList);
		return reservationList == 0;
	}

	private AmazonDynamoDB getClient() {
		return AmazonDynamoDBClientBuilder.standard()
				.withRegion(REGION)
				.build();
	}


}