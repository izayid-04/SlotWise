package com.izayid.slotwise.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.izayid.slotwise.auth.dto.RegisterRequest;
import com.izayid.slotwise.utilisateur.Role;
import com.izayid.slotwise.utilisateur.Utilisateur;
import com.izayid.slotwise.utilisateur.UtilisateurRepository;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Utilisateur register(RegisterRequest request) {
        if (utilisateurRepository.existsByEmail(request.email())) {
            throw new EmailDejaUtiliseException(request.email());
        }

        Utilisateur utilisateur = new Utilisateur(
                request.nom(),
                request.email(),
                passwordEncoder.encode(request.motDePasse()),
                Role.USER
        );

        return utilisateurRepository.save(utilisateur);
    }
}
