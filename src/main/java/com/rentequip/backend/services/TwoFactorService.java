package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.TwoFactorDisableRequest;
import com.rentequip.backend.dtos.request.TwoFactorSetupRequest;
import com.rentequip.backend.dtos.request.TwoFactorVerifyRequest;
import com.rentequip.backend.dtos.response.TwoFactorChallengeResponse;
import com.rentequip.backend.dtos.response.UserResponse;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.entities.VerificationCode;
import com.rentequip.backend.enums.TwoFactorChannel;
import com.rentequip.backend.enums.VerificationPurpose;
import com.rentequip.backend.exceptions.RentEquipException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.exceptions.VerificationCodeException;
import com.rentequip.backend.mappers.UserMapper;
import com.rentequip.backend.notifications.SmsGatewayClient;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.repositories.VerificationCodeRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Two-step verification: after the password, a six-digit code sent by email or SMS. The same codes
 * confirm the channel when the user turns the protection on, so nobody locks themselves out with a
 * phone or inbox they cannot read.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    /** A new code cannot be asked for before this, so the login form cannot be used to flood a phone. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SmsGatewayClient smsGatewayClient;

    /** Called by the login once the password matched, for a user who has the protection on. */
    @Transactional
    public TwoFactorChallengeResponse startLogin(User user) {
        return sendCode(user, VerificationPurpose.LOGIN, user.getTwoFactorChannel());
    }

    /**
     * Checks a sign-in code and returns its user. A wrong code still counts as an attempt, so the
     * transaction commits even though the call fails.
     */
    @Transactional(noRollbackFor = RentEquipException.class)
    public User verifyLogin(TwoFactorVerifyRequest request) {
        VerificationCode code = codeRepository.findByChallengeId(request.challengeId())
                .orElseThrow(VerificationCodeException::expired);
        consume(code, request.code(), VerificationPurpose.LOGIN);
        return code.getUser();
    }

    /** Sends a code to the chosen channel; the protection is only turned on once that code comes back. */
    @Transactional
    public TwoFactorChallengeResponse startSetup(TwoFactorSetupRequest request) {
        return sendCode(me(), VerificationPurpose.SETUP, request.channel());
    }

    @Transactional(noRollbackFor = RentEquipException.class)
    public UserResponse confirmSetup(TwoFactorVerifyRequest request) {
        User user = me();
        VerificationCode code = codeRepository.findByChallengeId(request.challengeId())
                .filter(found -> found.getUser().getId().equals(user.getId()))
                .orElseThrow(VerificationCodeException::expired);
        consume(code, request.code(), VerificationPurpose.SETUP);
        user.setTwoFactorChannel(code.getChannel());
        return userMapper.toResponse(user);
    }

    /** Turning the protection off asks for the password, so an unattended open session cannot do it. */
    @Transactional
    public UserResponse disable(TwoFactorDisableRequest request) {
        User user = me();
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw VerificationCodeException.wrongPassword();
        }
        user.setTwoFactorChannel(null);
        return userMapper.toResponse(user);
    }

    private TwoFactorChallengeResponse sendCode(User user, VerificationPurpose purpose, TwoFactorChannel channel) {
        codeRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .map(last -> Duration.between(last.getCreatedAt(), LocalDateTime.now()))
                .filter(elapsed -> elapsed.compareTo(RESEND_COOLDOWN) < 0)
                .ifPresent(elapsed -> {
                    throw VerificationCodeException.tooSoon(Math.max(1, RESEND_COOLDOWN.minus(elapsed).toSeconds()));
                });

        String plainCode = "%06d".formatted(RANDOM.nextInt(1_000_000));
        String destination = deliver(user, channel, plainCode, purpose);

        codeRepository.voidPending(user.getId(), purpose);
        VerificationCode code = new VerificationCode();
        code.setChallengeId(UUID.randomUUID().toString());
        code.setUser(user);
        code.setPurpose(purpose);
        code.setChannel(channel);
        code.setCodeHash(passwordEncoder.encode(plainCode));
        code.setExpiresAt(LocalDateTime.now().plus(CODE_TTL));
        codeRepository.save(code);
        return new TwoFactorChallengeResponse(code.getChallengeId(), channel, destination, CODE_TTL.toSeconds());
    }

    /** Sends the code and returns where it went, masked for the screen. */
    private String deliver(User user, TwoFactorChannel channel, String plainCode, VerificationPurpose purpose) {
        long minutes = CODE_TTL.toMinutes();
        return switch (channel) {
            case EMAIL -> {
                emailService.sendOrFail(user.getEmail(), plainCode + " es tu código de RentEquip", "verification-code",
                        Map.of("code", plainCode, "minutes", minutes, "name", user.getFirstName(),
                                "action", purpose == VerificationPurpose.LOGIN
                                        ? "iniciar sesión" : "activar la verificación en dos pasos"));
                yield ReceiptService.maskEmail(user.getEmail());
            }
            case SMS -> {
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    throw VerificationCodeException.phoneRequired();
                }
                String phone = ReceiptService.toE164(user.getPhone());
                smsGatewayClient.send(phone, "RentEquip: tu codigo es " + plainCode + ". Vence en " + minutes
                        + " minutos. No lo compartas con nadie.");
                yield ReceiptService.maskPhone(phone);
            }
        };
    }

    private void consume(VerificationCode code, String plainCode, VerificationPurpose purpose) {
        if (code.getPurpose() != purpose || code.isUsed() || code.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw VerificationCodeException.expired();
        }
        if (!passwordEncoder.matches(plainCode, code.getCodeHash())) {
            code.setAttempts(code.getAttempts() + 1);
            int left = MAX_ATTEMPTS - code.getAttempts();
            if (left <= 0) {
                code.setUsed(true);
            }
            throw VerificationCodeException.invalid(left);
        }
        code.setUsed(true);
    }

    private User me() {
        Long userId = currentUser.requireUserId();
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }
}
