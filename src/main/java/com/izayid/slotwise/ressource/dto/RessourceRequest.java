package com.izayid.slotwise.ressource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RessourceRequest(
        @NotBlank(message = "Le nom est obligatoire")
        String nom,

        String description,

        @NotNull(message = "La capacité est obligatoire")
        @Positive(message = "La capacité doit être supérieure à 0")
        Integer capacite,

        @NotNull(message = "La disponibilité est obligatoire")
        Boolean disponible
) {
}
