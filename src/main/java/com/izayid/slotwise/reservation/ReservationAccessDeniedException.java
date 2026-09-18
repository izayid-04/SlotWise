package com.izayid.slotwise.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ReservationAccessDeniedException extends RuntimeException {

    public ReservationAccessDeniedException() {
        super("Vous ne pouvez annuler que vos propres réservations");
    }
}
