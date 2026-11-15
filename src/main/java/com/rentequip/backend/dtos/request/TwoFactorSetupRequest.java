package com.rentequip.backend.dtos.request;

import com.rentequip.backend.enums.TwoFactorChannel;
import jakarta.validation.constraints.NotNull;

public record TwoFactorSetupRequest(@NotNull TwoFactorChannel channel) {
}
