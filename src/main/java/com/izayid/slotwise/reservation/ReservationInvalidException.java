package com.izayid.slotwise.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ReservationInvalidException extends RuntimeException {

    public ReservationInvalidException(String message) {
        super(message);
    }
}
