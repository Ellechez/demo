package com.task10;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class ReservationResponse {
    private List<Reservation> reservations;
    public ReservationResponse(final List<Reservation> reservations) {
        this.reservations = reservations;
    }


}
