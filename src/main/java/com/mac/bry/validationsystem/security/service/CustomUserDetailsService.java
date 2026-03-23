package com.mac.bry.validationsystem.security.service;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Obługuje logowanie zarówny dla username jak i adresu e-mail
        User user;
        if (username.contains("@")) {
            user = userRepository.findByEmail(username)
                    .orElseThrow(
                            () -> new UsernameNotFoundException("Nie znaleziono użytkownika po e-mailu: " + username));
        } else {
            user = userRepository.findByUsername(username)
                    .orElseThrow(
                            () -> new UsernameNotFoundException("Nie znaleziono użytkownika po nazwie: " + username));
        }

        return user;
    }
}
