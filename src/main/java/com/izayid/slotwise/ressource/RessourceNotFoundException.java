package com.izayid.slotwise.ressource;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RessourceNotFoundException extends RuntimeException {

    public RessourceNotFoundException(Long id) {
        super("Ressource introuvable avec l'id : " + id);
    }
}
