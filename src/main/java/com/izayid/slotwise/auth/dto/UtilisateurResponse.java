package com.izayid.slotwise.auth.dto;

import com.izayid.slotwise.utilisateur.Role;
import com.izayid.slotwise.utilisateur.Utilisateur;

public record UtilisateurResponse(
        Long id,
        String nom,
        String email,
        Role role
) {
    public static UtilisateurResponse fromEntity(Utilisateur utilisateur) {
        return new UtilisateurResponse(
                utilisateur.getId(),
                utilisateur.getNom(),
                utilisateur.getEmail(),
                utilisateur.getRole()
        );
    }
}
