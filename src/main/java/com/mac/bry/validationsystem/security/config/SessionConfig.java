package com.mac.bry.validationsystem.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

/**
 * Konfiguracja wyrzucona do osobnej klasy, aby złamać cykl zależności
 * w kontenerze IoC Springa (SecurityConfig <-> SessionSecurityFilter <->
 * SessionManagementService)
 */
@Configuration
public class SessionConfig {

    @Bean
    public SessionRegistry sessionRegistry() {
        System.out.println("🔧 SESSION CONFIG: Initializing SessionRegistry bean");
        return new SessionRegistryImpl();
    }
}
