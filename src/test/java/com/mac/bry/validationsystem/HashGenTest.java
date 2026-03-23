package com.mac.bry.validationsystem;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenTest {
    @Test
    public void gen() {
        System.out.println("\n\n===HASH_START===" + new BCryptPasswordEncoder(12).encode("***REMOVED***") + "===HASH_END===\n\n");
    }
}
