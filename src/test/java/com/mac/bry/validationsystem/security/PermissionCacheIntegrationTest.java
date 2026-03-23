package com.mac.bry.validationsystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.mac.bry.validationsystem.security.UserPermission;
import com.mac.bry.validationsystem.security.UserPermission;
import com.mac.bry.validationsystem.security.repository.UserPermissionRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.context.annotation.Import;
import com.mac.bry.validationsystem.config.TestMailConfig;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public class PermissionCacheIntegrationTest {

        @Autowired
        private PermissionService permissionService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CompanyRepository companyRepository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserPermissionRepository userPermissionRepository;

        @Autowired
        private EntityManager entityManager;

        private User testUser;
        private Company testCompany;

        @BeforeEach
        void setUp() {
                testCompany = new Company();
                testCompany.setName("Cache Test Company");
                testCompany = companyRepository.save(testCompany);

                testUser = new User();
                testUser.setUsername("cacheuser");
                testUser.setPassword("password");
                testUser.setEmail("cache@example.com");
                testUser = userRepository.save(testUser);
        }

        @Test
        @Transactional
        void cacheRebuildsOnPermissionChange() throws Exception {
                // Nadać uprawnienie na poziomie firmy
                UserPermission savedPerm = permissionService.grantFullCompanyAccess(testUser.getId(), testCompany,
                                null);

                // Wymusić flush żeby zapis z Hibernate wpadł do bazy przed zapytaniem JDBC
                userPermissionRepository.flush();
                userRepository.flush();
                entityManager.clear();

                // Ze względu na specyfikę MySQL JSON, wyciągniemy go jako String by sprawdzić
                // zawartość
                String jsonStr = jdbcTemplate.queryForObject(
                                "SELECT permissions_cache_json FROM users WHERE id = ?",
                                String.class,
                                testUser.getId());

                assertNotNull(jsonStr, "Cache JSON should not be null after granting permission");

                com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(jsonStr);
                if (jsonNode.isTextual()) {
                        jsonNode = objectMapper.readTree(jsonNode.asText());
                }

                // Assert json values
                assertTrue(jsonNode.has("allowedCompanyIds"));
                assertTrue(jsonNode.get("allowedCompanyIds").toString().contains(testCompany.getId().toString()),
                                "Cache should contain the granted company ID");

                // Usunąć uprawnienie
                permissionService.revokePermission(savedPerm.getId());
                userPermissionRepository.flush();
                userRepository.flush();
                entityManager.clear();

                String updatedJsonStr = jdbcTemplate.queryForObject(
                                "SELECT permissions_cache_json FROM users WHERE id = ?",
                                String.class,
                                testUser.getId());

                assertNotNull(updatedJsonStr);
                com.fasterxml.jackson.databind.JsonNode updatedJsonNode = objectMapper.readTree(updatedJsonStr);
                if (updatedJsonNode.isTextual()) {
                        updatedJsonNode = objectMapper.readTree(updatedJsonNode.asText());
                }
                boolean hasCompanyId = updatedJsonNode.has("allowedCompanyIds") &&
                                updatedJsonNode.get("allowedCompanyIds").toString()
                                                .contains(testCompany.getId().toString());
                org.junit.jupiter.api.Assertions.assertFalse(hasCompanyId,
                                "Cache should not contain the revoked company ID");
        }
}
