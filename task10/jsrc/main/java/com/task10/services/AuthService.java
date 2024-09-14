package com.task10.services;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

public class AuthService {

    private final CognitoIdentityProviderClient cognitoIdentityProviderClient = CognitoIdentityProviderClient.builder().build();
    private final String cognitoId = System.getenv("COGNITO_ID");
    private final String clientId = System.getenv("CLIENT_ID");
    private String email;
    private String password;

    public APIGatewayProxyResponseEvent signUp(APIGatewayProxyRequestEvent request) {
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
            cognitoIdentityProviderClient.adminCreateUser(adminCreateUserRequest);

            AdminSetUserPasswordRequest adminSetUserPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(cognitoId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();
            cognitoIdentityProviderClient.adminSetUserPassword(adminSetUserPasswordRequest);

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
                            .put("accessToken", cognitoIdentityProviderClient
                                    .adminInitiateAuth(authRequest)
                                    .authenticationResult()
                                    .idToken())
                            .toString());

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error: " + e.getMessage());
        }
    }
}