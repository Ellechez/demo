package com.task11;

public record Reservation(Number tableNumber, String clientName, String phoneNumber, String date, String slotTimeStart, String slotTimeEnd) {
}
