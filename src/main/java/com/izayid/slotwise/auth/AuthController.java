package com.izayid.slotwise.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.izayid.slotwise.auth.dto.RegisterRequest;
import com.izayid.slotwise.auth.dto.UtilisateurResponse;
import com.izayid.slotwise.utilisateur.Utilisateur;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UtilisateurResponse> register(@Valid @RequestBody RegisterRequest request) {
        Utilisateur utilisateur = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UtilisateurResponse.fromEntity(utilisateur));
    }
}
