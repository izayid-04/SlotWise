package com.izayid.slotwise.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.izayid.slotwise.auth.dto.LoginRequest;
import com.izayid.slotwise.auth.dto.RegisterRequest;
import com.izayid.slotwise.utilisateur.Role;
import com.izayid.slotwise.utilisateur.Utilisateur;
import com.izayid.slotwise.utilisateur.UtilisateurRepository;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UtilisateurDetailsService utilisateurDetailsService;
    private final JwtService jwtService;

    public AuthService(UtilisateurRepository utilisateurRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        UtilisateurDetailsService utilisateurDetailsService,
                        JwtService jwtService) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.utilisateurDetailsService = utilisateurDetailsService;
        this.jwtService = jwtService;
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

    public String login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse())
            );
        } catch (BadCredentialsException e) {
            throw new IdentifiantsInvalidesException();
        }

        UserDetails userDetails = utilisateurDetailsService.loadUserByUsername(request.email());
        return jwtService.generateToken(userDetails);
    }
}
