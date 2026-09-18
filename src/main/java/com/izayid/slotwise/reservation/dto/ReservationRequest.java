package com.izayid.slotwise.reservation.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull(message = "La ressource est obligatoire")
        Long ressourceId,

        @NotNull(message = "La date de début est obligatoire")
        LocalDateTime dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDateTime dateFin
) {
}