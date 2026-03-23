package com.mac.bry.validationsystem.audit;

import com.mac.bry.validationsystem.security.User;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Wypełnia pola rewizji Envers danymi zalogowanego użytkownika i adresem IP.
 *
 * UWAGA: RevisionListener NIE jest Spring Beanem — @Autowired tu nie działa.
 * SecurityContextHolder i RequestContextHolder działają przez ThreadLocal.
 */
public class EnversRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        SystemRevisionEntity rev = (SystemRevisionEntity) revisionEntity;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            rev.setUserId(user.getId());
            rev.setUsername(user.getUsername());
            rev.setFullName(user.getFullName());
        } else if (auth != null && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            rev.setUsername(String.valueOf(auth.getPrincipal()));
        } else {
            rev.setUsername("system");
        }

        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                rev.setIpAddress(xForwardedFor != null && !xForwardedFor.isBlank()
                        ? xForwardedFor.split(",")[0].trim()
                        : request.getRemoteAddr());
            }
        } catch (Exception ignored) {
            // Poza kontekstem HTTP (np. @Scheduled, testy)
        }
    }
}
