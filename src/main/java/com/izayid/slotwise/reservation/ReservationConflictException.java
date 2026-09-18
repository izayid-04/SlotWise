package com.izayid.slotwise.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ReservationConflictException extends RuntimeException {

    public ReservationConflictException() {
        super("Ce créneau est déjà réservé pour cette ressource");
    }
}
