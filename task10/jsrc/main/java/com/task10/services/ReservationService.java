package com.task10.services;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReservationService {
    private static final String TABLE_NAME = "cmtr-341056d4-Reservations-test";
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TableService tableService;

    @AllArgsConstructor
    @Data
    final static class ReservationObject {
        private int tableNumber;
        private String clientName;
        private String phoneNumber;
        private String date;
        private String slotTimeStart;
        private String slotTimeEnd;
    }


    public ReservationService(TableService tableService) {
        this.tableService = tableService;
    }

    public APIGatewayProxyResponseEvent handleCreateReservation(APIGatewayProxyRequestEvent requestEvent, Context context) {
        JSONObject json = new JSONObject(requestEvent.getBody());
        int tableNumber = json.getInt("tableNumber");

        if (!tableService.isTableExist(tableNumber)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Not found");
        }
        String regexDate = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";
        String regexTime = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$";
        String date = json.getString("date");
        String slotTimeStart = json.getString("slotTimeStart");
        String slotTimeEnd = json.getString("slotTimeEnd");

        if (isOverlapping(tableNumber, date, context)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Overlapping");
        }

        if (!date.matches(regexDate)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid format: " + date);
        }
        if (!slotTimeStart.matches(regexTime) ||
                !slotTimeEnd.matches(regexTime)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid format: " + date);
        }

        String reservationId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(reservationId).build());
        item.put("tableNumber", AttributeValue.builder().n(Integer.toString(tableNumber)).build());
        item.put("clientName", AttributeValue.builder().s(json.getString("clientName")).build());
        item.put("date", AttributeValue.builder().s(date).build());
        item.put("phoneNumber", AttributeValue.builder().s(json.getString("phoneNumber")).build());
        item.put("slotTimeStart", AttributeValue.builder().s(slotTimeStart).build());
        item.put("slotTimeEnd", AttributeValue.builder().s(slotTimeEnd).build());

        PutItemRequest request = PutItemRequest.builder().tableName(TABLE_NAME).item(item).build();

        dynamoDbClient.putItem(request);
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(new JSONObject()
                        .put("reservationId", reservationId)
                        .toString());
    }

    private boolean isOverlapping(int tableNumber, String date, Context context) {
        context.getLogger().log("table:" + tableNumber + " date:" + date);
        return getReservations().stream()
                .peek(reservationObject -> context.getLogger().log("reservation:" + reservationObject))
                .anyMatch(reservationObject -> reservationObject.getTableNumber() == tableNumber
                        && reservationObject.getDate().equals(date));
    }

    public APIGatewayProxyResponseEvent handleReservations() {
        try {
            List<ReservationObject> reservationObjects = getReservations();
            Map<String, List<ReservationObject>> responseBody = new HashMap<>();
            responseBody.put("reservations", reservationObjects);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(responseBody));
        } catch (DynamoDbException | IOException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Error: " + e.getMessage());
        }

    }

    private List<ReservationObject> getReservations() {
        return dynamoDbClient.scan(ScanRequest.builder().tableName(TABLE_NAME).build())
                .items()
                .stream()
                .map(item -> new ReservationObject(Integer.parseInt((String) item.get("tableNumber").n()),
                        item.get("clientName").s(),
                        item.get("phoneNumber").s(),
                        item.get("date").s(),
                        item.get("slotTimeStart").s(),
                        item.get("slotTimeEnd").s()))
                .collect(Collectors.toList());

    }
}