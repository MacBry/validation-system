package com.mac.bry.validationsystem;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.security.PermissionType;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev") // We want the DataInitializer to run if possible, but it's !test
@Transactional
public class DataInitializerUserTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    @Test
    void shouldInitializeAdminUser() {
        // Given
        userPermissionRepository.deleteAll(); // Delete permissions first to avoid FK constraint
        userRepository.deleteAll(); // Force initialization
        
        // When
        dataInitializer.run();

        // Then
        User admin = userRepository.findByUsername("admin").orElse(null);
        assertThat(admin).isNotNull();
        assertThat(admin.getEmail()).isEqualTo("admin@validationsystem.com");
        assertThat(admin.getRoles()).anyMatch(role -> role.getName().equals("ROLE_ADMIN"));
        
        // Check permissions cache
        assertThat(admin.getPermissionsCacheJson()).contains("FULL_COMPANY");
    }
}
