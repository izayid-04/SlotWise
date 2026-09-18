package com.izayid.slotwise.ressource.dto;

import com.izayid.slotwise.ressource.Ressource;

public record RessourceResponse(
        Long id,
        String nom,
        String description,
        Integer capacite,
        Boolean disponible
) {
    public static RessourceResponse fromEntity(Ressource ressource) {
        return new RessourceResponse(
                ressource.getId(),
                ressource.getNom(),
                ressource.getDescription(),
                ressource.getCapacite(),
                ressource.getDisponible()
        );
    }
}
