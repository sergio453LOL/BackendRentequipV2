package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Single reader of the SecurityContext. Services depend on this instead of touching
 * SecurityContextHolder directly, so the identity has exactly one source and the ownership rules stay
 * unit testable by authenticating a context instead of passing an id around.
 */
@Component
public class CurrentUser {

    public CompanyUserDetails require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CompanyUserDetails principal)) {
            throw new UnauthorizedException("This operation requires an authenticated user");
        }
        return principal;
    }

    public Long requireCompanyId() {
        return require().companyId();
    }

    public Long requireUserId() {
        return require().userId();
    }

    /** Request attribute holding the demo visit id of the access token, set by the JWT filter. */
    public static final String DEMO_SESSION_ATTRIBUTE = "rentequip.demoSession";

    /** The one-click demo visit this request belongs to, or null for a normal session or outside a request. */
    public String demoSession() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(DEMO_SESSION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof String session ? session : null;
    }
}
