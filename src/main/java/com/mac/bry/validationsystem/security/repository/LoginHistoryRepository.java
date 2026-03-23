package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    List<LoginHistory> findByUserIdOrderByLoginTimeDesc(Long userId);

    List<LoginHistory> findByUsernameOrderByLoginTimeDesc(String username);

    int countByUsernameAndSuccessFalseAndLoginTimeAfter(String username, LocalDateTime time);

    int countByIpAddressAndLoginTimeAfter(String ipAddress, LocalDateTime time);
}
