package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.VerificationCode;
import com.rentequip.backend.enums.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    @Query("SELECT v FROM VerificationCode v JOIN FETCH v.user u JOIN FETCH u.company WHERE v.challengeId = :challengeId")
    Optional<VerificationCode> findByChallengeId(@Param("challengeId") String challengeId);

    /** The newest code sent to the user for this purpose, to space out new sends. */
    Optional<VerificationCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, VerificationPurpose purpose);

    /** Sending a new code voids the pending ones, so only the last message received is valid. */
    @Modifying
    @Query("UPDATE VerificationCode v SET v.used = true WHERE v.user.id = :userId AND v.purpose = :purpose AND v.used = false")
    void voidPending(@Param("userId") Long userId, @Param("purpose") VerificationPurpose purpose);

}
