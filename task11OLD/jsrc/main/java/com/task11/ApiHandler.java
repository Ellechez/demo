package com.task11;

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

	private final AmazonDynamoDB amazonDynamoDB =
			AmazonDynamoDBClientBuilder.standard().withRegion(System.getenv("region")).build();

	private final CognitoIdentityProviderClient cognitoIdentityProviderClient =
			CognitoIdentityProviderClient.builder().region(Region.of(System.getenv("region"))).build();

	@Override
	public APIGatewayV2HTTPResponse handleRequest(ApiRequest requestEvent, Context context) {
		return switch(requestEvent.path()) {
			case "/signup" -> {
				var userPoolId = getUserPoolId();
				yield signUp(requestEvent, userPoolId);
			}
			case "/signin" -> {
				var userPoolId = getUserPoolId();
				var clientId = createClient(userPoolId);
				yield signIn(requestEvent, userPoolId, clientId);
			}
			case "/tables" -> {
				if(requestEvent.method().equals("POST")) {
					var tableObject = createTableObject(requestEvent);
					yield handleTable(tableObject);
				} else {
					yield scanTable();
				}
			}
			case "/reservations" -> {
				if(requestEvent.method().equals("POST")) {
					var reservationObject = createReservationObject(requestEvent);
					yield handleReservation(reservationObject);
				} else {
					yield scanReservations();
				}
			}
			default -> {
				System.out.println("Processing" +  requestEvent.authorization_header());
				yield findTable(requestEvent.authorization_header());
			}
		};
	}

	private Table createTableObject(ApiRequest apiRequest) {
		return new Table(Integer.valueOf(apiRequest.body_json().get("id")), Integer.valueOf(apiRequest.body_json().get("number")),
				Integer.valueOf(apiRequest.body_json().get("places")), Boolean.valueOf(apiRequest.body_json().get("isVip")),
				Objects.nonNull(apiRequest.body_json().get("minOrder")) ? Integer.parseInt(apiRequest.body_json().get("minOrder")) : null);
	}

	private Reservation createReservationObject(ApiRequest apiRequest) {
		return new Reservation(Integer.valueOf(apiRequest.body_json().get("tableNumber")), apiRequest.body_json().get("clientName"),
				apiRequest.body_json().get("phoneNumber"), apiRequest.body_json().get("date"),
				apiRequest.body_json().get("slotTimeStart"), apiRequest.body_json().get("slotTimeEnd"));
	}

	private Table createTableResponse(Map<String, AttributeValue> result) {
		return new Table(Integer.valueOf(result.get("id").getN()), Integer.valueOf(result.get("number").getN()),
				Integer.valueOf(result.get("places").getN()), result.get("isVip").getBOOL(),
				Objects.nonNull(result.get("minOrder")) ? (Integer.valueOf(result.get("minOrder").getN())) : null);
	}

	private Reservation createReservationResponse(Map<String, AttributeValue> result) {
		return new Reservation(Integer.valueOf(result.get("tableNumber").getN()), result.get("clientName").getS(), result.get("phoneNumber").getS(),
				result.get("date").getS(), result.get("slotTimeStart").getS(), result.get("slotTimeEnd").getS());
	}

	private String createClient(String userPoolId) {
		return cognitoIdentityProviderClient
				.createUserPoolClient(CreateUserPoolClientRequest
						.builder()
						.userPoolId(userPoolId)
						.explicitAuthFlows(ExplicitAuthFlowsType.ALLOW_ADMIN_USER_PASSWORD_AUTH, ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH)
						.clientName("api_client")
						.build())
				.userPoolClient()
				.clientId();
	}

	private APIGatewayV2HTTPResponse signUp(ApiRequest apiRequest, String userPoolId) {
		try {
			var attributes = new ArrayList<AttributeType>();
			attributes.add(AttributeType.builder().name("email").value(apiRequest.body_json().get("email")).build());
			var userRequest = AdminCreateUserRequest.builder()
					.temporaryPassword(apiRequest.body_json().get("password"))
					.userPoolId(userPoolId)
					.username(apiRequest.body_json().get("email"))
					.messageAction(MessageActionType.SUPPRESS)
					.userAttributes(attributes).build();
			cognitoIdentityProviderClient.adminCreateUser(userRequest);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).build();
		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while signing up user " + e.awsErrorDetails().errorMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private Map<String, String> createHeaders() {
		var map = new HashMap<String, String>();
		map.put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token");
		map.put("Access-Control-Allow-Origin", "*");
		map.put("Access-Control-Allow-Methods", "*");
		map.put("Accept-Version", "*");
		return map;
	}

	private APIGatewayV2HTTPResponse signIn(ApiRequest apiRequest, String userPoolId, String clientId) {

		var authRequest = AdminInitiateAuthRequest.builder()
				.authFlow("ADMIN_USER_PASSWORD_AUTH")
				.authParameters(Map.of(
						"USERNAME", apiRequest.body_json().get("email"),
						"PASSWORD", apiRequest.body_json().get("password")))
				.userPoolId(userPoolId)
				.clientId(clientId)
				.build();

		try {
			var authResponse = cognitoIdentityProviderClient.adminInitiateAuth(authRequest);
			var authResult = authResponse.authenticationResult();
			if(Objects.nonNull(authResponse.challengeName()) && authResponse.challengeName().equals(ChallengeNameType.NEW_PASSWORD_REQUIRED)) {
				var adminRespondToAuthChallengeResponse = cognitoIdentityProviderClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
						.userPoolId(userPoolId)
						.clientId(clientId)
						.session(authResponse.session())
						.challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
						.challengeResponses(
								Map.of("NEW_PASSWORD", apiRequest.body_json().get("password"),
										"USERNAME", apiRequest.body_json().get("email"))).build());
				authResult = adminRespondToAuthChallengeResponse.authenticationResult();
			}
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(authResult.idToken()).build();
		} catch (Exception e) {
			System.err.println("Error while signing in user " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private String getUserPoolId() {
		var descriptionType = UserPoolDescriptionType.builder().id("test-id").build();
		try {
			var request = ListUserPoolsRequest.builder().maxResults(50).build();
			var response = cognitoIdentityProviderClient.listUserPools(request);
			descriptionType = response.userPools().stream().filter(
					value -> value.name().equals(
							System.getenv("booking_userpool"))).findFirst().orElse(descriptionType);
			return descriptionType.id();

		} catch (CognitoIdentityProviderException e) {
			System.err.println("Error while listing the user pools: " + e.awsErrorDetails().errorMessage());
		}
		return descriptionType.id();
	}

	private APIGatewayV2HTTPResponse handleTable(Table table) {
		try {
			var attributes = new HashMap<String, AttributeValue>();
			attributes.put("id", new AttributeValue().withN(String.valueOf(table.id())));
			attributes.put("number", new AttributeValue().withN(String.valueOf(table.number())));
			attributes.put("places", new AttributeValue().withN(String.valueOf(table.places())));
			attributes.put("isVip", new AttributeValue().withBOOL(table.isVip()));
			if (Objects.nonNull(table.minOrder())) {
				attributes.put("minOrder", new AttributeValue().withN(String.valueOf(table.minOrder())));
			}
			amazonDynamoDB.putItem(System.getenv("tables_table"), attributes);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(String.valueOf(table.id())).build();
		} catch(Exception e) {
			System.err.println("Error while persisting table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanTable() {
		try {
			var list = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
					.getItems().stream().map(this::createTableResponse).toList();
			var apiResponse = new TableResponse(list);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse findTable(String tableId) {
		try {
			var attributesMap = new HashMap<String, AttributeValue>();
			attributesMap.put("id", new AttributeValue().withN(String.valueOf(tableId)));
			var result = amazonDynamoDB.getItem(System.getenv("tables_table"), attributesMap).getItem();
			var tableResult = createTableResponse(result);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(objectMapper.writeValueAsString(tableResult)).build();
		} catch (Exception e) {
			System.err.println("Error while finding table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse scanReservations() {
		try {
			var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
					.getItems().stream().map(this::createReservationResponse).toList();
			var apiResponse = new ReservationResponse(reservationList);
			return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(objectMapper.writeValueAsString(apiResponse)).build();
		} catch (Exception e) {
			System.err.println("Error while scanning table " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private APIGatewayV2HTTPResponse handleReservation(Reservation reservation) {
		try {
			if(checkTable(reservation) && checkReservation(reservation)) {
				var attributes = new HashMap<String, AttributeValue>();
				attributes.put("id", new AttributeValue(UUID.randomUUID().toString()));
				attributes.put("tableNumber", new AttributeValue().withN(String.valueOf(reservation.tableNumber())));
				attributes.put("clientName", new AttributeValue(String.valueOf(reservation.clientName())));
				attributes.put("phoneNumber", new AttributeValue(String.valueOf(reservation.phoneNumber())));
				attributes.put("date", new AttributeValue(reservation.date()));
				attributes.put("slotTimeStart", new AttributeValue(String.valueOf(reservation.slotTimeStart())));
				attributes.put("slotTimeEnd", new AttributeValue(String.valueOf(reservation.slotTimeEnd())));
				amazonDynamoDB.putItem(System.getenv("reservations_table"), attributes);
				return APIGatewayV2HTTPResponse.builder().withStatusCode(200).withHeaders(createHeaders()).withBody(UUID.randomUUID().toString()).build();
			} else {
				return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withBody("ERROR, there is already a reservation or the table does not exist").build();
			}
		} catch(Exception e) {
			System.err.println("Error while persisting reservation " + e.getMessage());
			return APIGatewayV2HTTPResponse.builder().withStatusCode(400).withHeaders(createHeaders()).withBody("ERROR " + e.getMessage()).build();
		}
	}

	private boolean checkTable(Reservation reservation) {
		var tableList = amazonDynamoDB.scan(new ScanRequest(System.getenv("tables_table")))
				.getItems().stream().map(this::createTableResponse).filter(value -> reservation.tableNumber().equals(value.number())).count();
		return tableList == 1;
	}

	private boolean checkReservation(Reservation reservation) {
		var reservationList = amazonDynamoDB.scan(new ScanRequest(System.getenv("reservations_table")))
				.getItems().stream().map(this::createReservationResponse)
				.filter(value ->
						value.tableNumber().equals(reservation.tableNumber()) && value.slotTimeStart().equals(reservation.slotTimeStart())
								&& value.slotTimeEnd().equals(reservation.slotTimeEnd())).count();
		return reservationList == 0;
	}

}