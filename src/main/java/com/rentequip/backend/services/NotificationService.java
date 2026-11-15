package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.NotificationResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.entities.Notification;
import com.rentequip.backend.enums.NotificationType;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.NotificationRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** The company's in-app inbox: create alerts, list them, count the unread ones, mark and clear them. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUser currentUser;

    /**
     * Joins the caller's transaction on purpose: the alert is saved or rolled back together with the
     * booking or offer it announces, so nobody is told about something that did not happen.
     */
    @Transactional
    public void notify(Long companyId, NotificationType type, String title, String message, String link) {
        Notification notification = new Notification();
        notification.setCompany(companyRepository.getReferenceById(companyId));
        notification.setType(type);
        notification.setTitle(truncate(title, 150));
        notification.setMessage(truncate(message, 500));
        notification.setLink(link);
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> findMine(int page, int size) {
        return PageResponse.from(notificationRepository.findByCompanyIdOrderByCreatedAtDesc(
                currentUser.requireCompanyId(), PageRequest.of(page, size)), NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        return notificationRepository.countByCompanyIdAndReadAtIsNull(currentUser.requireCompanyId());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification notification = notificationRepository.findByIdAndCompanyId(id, currentUser.requireCompanyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(currentUser.requireCompanyId(), LocalDateTime.now());
    }

    @Transactional
    public void delete(Long id) {
        Notification notification = notificationRepository.findByIdAndCompanyId(id, currentUser.requireCompanyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notificationRepository.delete(notification);
    }

    @Transactional
    public int deleteAll() {
        return notificationRepository.deleteAllByCompany(currentUser.requireCompanyId());
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getLink(), notification.getReadAt() != null,
                notification.getCreatedAt());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
