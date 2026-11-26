package com.rentequip.backend.security;

import com.rentequip.backend.enums.ReceiptChannel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Locale;

/**
 * The limits that keep public or costly endpoints from being abused, all answered with 429:
 * <ul>
 *   <li>login: failures per email and per IP (the mobile app cannot send reCAPTCHA, so this is what stops
 *       password guessing); a successful login clears that email's failures;</li>
 *   <li>registration per IP;</li>
 *   <li>SUNAT/RENIEC lookups per IP and per day (they are public and spend a paid quota);</li>
 *   <li>receipts by SMS or WhatsApp per user: one per channel per cooldown and a daily cap.</li>
 * </ul>
 * The IP is the request's remote address, which behind Caddy is the client (forward-headers-strategy).
 */
@Component
public class AbuseGuard {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final RateLimiter limiter;
    private final int loginPerEmail;
    private final int loginPerIp;
    private final Duration loginWindow;
    private final int registerPerIp;
    private final int identityPerIp;
    private final int identityPerDay;
    private final Duration receiptCooldown;
    private final int receiptsPerDay;

    public AbuseGuard(RateLimiter limiter,
                      @Value("${rentequip.limits.login.failures-per-email:10}") int loginPerEmail,
                      @Value("${rentequip.limits.login.failures-per-ip:50}") int loginPerIp,
                      @Value("${rentequip.limits.login.window-minutes:15}") long loginWindowMinutes,
                      @Value("${rentequip.limits.register.per-ip-per-hour:10}") int registerPerIp,
                      @Value("${rentequip.limits.identity.per-ip-per-hour:40}") int identityPerIp,
                      @Value("${rentequip.limits.identity.per-day:1000}") int identityPerDay,
                      @Value("${rentequip.limits.receipts.cooldown-seconds:60}") long receiptCooldownSeconds,
                      @Value("${rentequip.limits.receipts.per-user-per-day:10}") int receiptsPerDay) {
        this.limiter = limiter;
        this.loginPerEmail = loginPerEmail;
        this.loginPerIp = loginPerIp;
        this.loginWindow = Duration.ofMinutes(loginWindowMinutes);
        this.registerPerIp = registerPerIp;
        this.identityPerIp = identityPerIp;
        this.identityPerDay = identityPerDay;
        this.receiptCooldown = Duration.ofSeconds(receiptCooldownSeconds);
        this.receiptsPerDay = receiptsPerDay;
    }

    /** Before checking the password: an email or IP with too many recent failures is turned away. */
    public void beforeLogin(String email) {
        limiter.check("login-email", normalize(email), loginPerEmail, loginWindow);
        limiter.check("login-ip", clientIp(), loginPerIp, loginWindow);
    }

    public void loginFailed(String email) {
        limiter.record("login-email", normalize(email), loginWindow);
        limiter.record("login-ip", clientIp(), loginWindow);
    }

    public void loginSucceeded(String email) {
        limiter.reset("login-email", normalize(email));
    }

    public void beforeRegister() {
        limiter.acquire("register-ip", clientIp(), registerPerIp, HOUR);
    }

    public void beforeIdentityLookup() {
        limiter.acquire("identity-ip", clientIp(), identityPerIp, HOUR);
        limiter.acquire("identity-day", "all", identityPerDay, DAY);
    }

    /** Only the paid channels are limited; the email copy costs nothing. */
    public void beforeReceipt(Long userId, ReceiptChannel channel) {
        if (channel == ReceiptChannel.EMAIL) {
            return;
        }
        String user = String.valueOf(userId);
        limiter.check("receipt-day", user, receiptsPerDay, DAY);
        limiter.acquire("receipt-" + channel.name(), user, 1, receiptCooldown);
        limiter.record("receipt-day", user, DAY);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
