package com.task10.services;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class AuthService {

    private final CognitoIdentityProviderClient identityProviderClient = CognitoIdentityProviderClient.builder().build();
    private final String cognitoId = System.getenv("COGNITO_ID");
    private final String clientId = System.getenv("CLIENT_ID");
    private String email;
    private String password;

    /*public APIGatewayProxyResponseEvent signUp(APIGatewayProxyRequestEvent request) {
        try {
            JSONObject json = new JSONObject(request.getBody());
            email = json.getString("email");
            password = json.getString("password");
            String regexEmail = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$";
            String regexPassword = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\$%\\^_\\-\\*])[A-Za-z\\d\\$%\\^_\\-\\*]{12,}$";

            if (!json.getString("email").matches(regexEmail)) {
                return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid email");
            }
            if (!json.getString("password").matches(regexPassword)) {
                return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid password");
            }

            AdminCreateUserRequest adminCreateUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(cognitoId)
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("given_name").value(json.getString("firstName")).build(),
                            AttributeType.builder().name("family_name").value(json.getString("lastName")).build(),
                            AttributeType.builder().name("email").value(email).build()
                    )
                    .temporaryPassword(password)
                    .build();
            identityProviderClient.adminCreateUser(adminCreateUserRequest);

            AdminSetUserPasswordRequest adminSetUserPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(cognitoId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();
            identityProviderClient.adminSetUserPassword(adminSetUserPasswordRequest);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject()
                    .put("message", "User has been successfully signed up.")
                    .toString());

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error: " + e.getMessage());
        }
    }

    public APIGatewayProxyResponseEvent signIn(APIGatewayProxyRequestEvent request) {
        try {
            JSONObject json = new JSONObject(request.getBody());
            email = json.getString("email");
            password = json.getString("password");

            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .userPoolId(cognitoId)
                    .clientId(clientId)
                    .authParameters(Map.of("USERNAME", email, "PASSWORD", password))
                    .build();

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject()
                            .put("accessToken", identityProviderClient
                                    .adminInitiateAuth(authRequest)
                                    .authenticationResult()
                                    .idToken())
                            .toString());

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error: " + e.getMessage());
        }
    }*/

    private APIGatewayProxyResponseEvent signUp(APIGatewayProxyRequestEvent request) {
        try {
            JSONObject json = new JSONObject(request.getBody());
            email = json.getString("email");
            password = json.getString("password");

            var userAttributeList = new ArrayList<AttributeType>();
            userAttributeList.add(AttributeType.builder().name("email").value(email).build());
            var adminCreateUserRequest = AdminCreateUserRequest.builder()
                    .temporaryPassword(password)
                    .userPoolId(getUserPoolId())
                    .username(email)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(userAttributeList).build();
            identityProviderClient.adminCreateUser(adminCreateUserRequest);
            System.out.println("User has been created ");
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject()
                            .put("message", "User has been successfully signed up.")
                            .toString());
        } catch (CognitoIdentityProviderException e) {
            System.err.println("Error while signing up user " + e.awsErrorDetails().errorMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("ERROR " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent signIn(APIGatewayProxyRequestEvent request) {
        JSONObject json = new JSONObject(request.getBody());
        email = json.getString("email");
        password = json.getString("password");

        var authRequest = AdminInitiateAuthRequest.builder()
                .authFlow("ADMIN_USER_PASSWORD_AUTH")
                .authParameters(Map.of(
                        "USERNAME", email,
                        "PASSWORD", password
                ))
                .userPoolId(getUserPoolId())
                .clientId(clientId)
                .build();

        try {
            var authResponse = identityProviderClient.adminInitiateAuth(authRequest);
            System.out.println("Auth response: " + authResponse + "session " + authResponse.session());
            var authResult = authResponse.authenticationResult();
            if(Objects.nonNull(authResponse.challengeName()) && authResponse.challengeName().equals(ChallengeNameType.NEW_PASSWORD_REQUIRED)) {
                var adminRespondToAuthChallengeResponse = identityProviderClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
                        .userPoolId(getUserPoolId())
                        .clientId(clientId)
                        .session(authResponse.session())
                        .challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED)
                        .challengeResponses(
                                Map.of("NEW_PASSWORD", password,
                                        "USERNAME", email)).build());
                System.out.println("Challenge passed: " + adminRespondToAuthChallengeResponse.authenticationResult().idToken());
                authResult = adminRespondToAuthChallengeResponse.authenticationResult();
            }
            // At this point, the user is successfully authenticated, and you can access JWT tokens:
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(authResult.idToken());
        } catch (Exception e) {
            System.err.println("Error while signing in user " + e.getMessage());
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("ERROR " + e.getMessage());
        }
    }

    private String getUserPoolId() {
        var userPoolDescriptionType = UserPoolDescriptionType.builder().id("test-id").build();
        try {
            var request = ListUserPoolsRequest.builder().maxResults(50).build();
            var response = identityProviderClient.listUserPools(request);
            userPoolDescriptionType = response.userPools().stream().filter(value -> value.name().equals(System.getenv("booking_userpool")))
                    .findFirst().orElse(userPoolDescriptionType);
            System.out.println("User pool id: " + userPoolDescriptionType.id());
            return userPoolDescriptionType.id();

        } catch (CognitoIdentityProviderException e) {
            System.err.println("Error while listing the user pools: " + e.awsErrorDetails().errorMessage());
        }
        return userPoolDescriptionType.id();
    }
}