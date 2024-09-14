package com.task10;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
public class Reservation {
    private Number tableNumber;
    private String clientName;
    private String phoneNumber;
    private String date;
    private String slotTimeStart;
    private String slotTimeEnd;

    public Reservation(ApiRequest apiRequest) {
        this.tableNumber = Integer.valueOf(apiRequest.getBody_json().get("tableNumber"));
        this.clientName = apiRequest.getBody_json().get("clientName");
        this.phoneNumber = apiRequest.getBody_json().get("phoneNumber");
        this.date = apiRequest.getBody_json().get("date");
        this.slotTimeStart = apiRequest.getBody_json().get("slotTimeStart");
        this.slotTimeEnd = apiRequest.getBody_json().get("slotTimeEnd");
    }

    public Reservation(Map<String, AttributeValue> result) {
        this.tableNumber = Integer.valueOf(result.get("tableNumber").getN());
        this.clientName = result.get("clientName").getS();
        this.phoneNumber = result.get("phoneNumber").getS();
        this.date = result.get("date").getS();
        this.slotTimeStart = result.get("slotTimeStart").getS();
        this.slotTimeEnd = result.get("slotTimeEnd").getS();
    }
}
