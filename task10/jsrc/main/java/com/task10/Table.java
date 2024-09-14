package com.task10;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import lombok.AllArgsConstructor;
import lombok.Data;


import java.util.Map;
import java.util.Objects;

@Data
public class Table {
    private Number id;
    private Number number;
    private Number places;
    private Boolean isVip;
    private Number minOrder;
    private ApiRequest apiRequest;
    private Map<String, String> body_json;

    public Table(ApiRequest apiRequest) {
        this.id = Integer.valueOf(body_json.get("id"));
        this.number = Integer.valueOf(body_json.get("number"));
        this.places = Integer.valueOf(body_json.get("places"));
        this.isVip = Boolean.valueOf(body_json.get("isVip"));
        this.minOrder = Objects.nonNull(body_json.get("minOrder")) ? Integer.parseInt(body_json.get("minOrder")) : null;

    }

    public Table(Map<String, AttributeValue> result) {
        this.id = Integer.valueOf(result.get("id").getN());
        this.number = Integer.valueOf(result.get("number").getN());
        this.places = Integer.valueOf(result.get("places").getN());
        this.isVip = result.get("isVip").getBOOL();
        this.minOrder = Objects.nonNull(result.get("minOrder")) ? (Integer.valueOf(result.get("minOrder").getN())) : null;
    }



}
