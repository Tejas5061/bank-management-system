package com.bankms.dto.account;

import com.bankms.entity.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountStatusRequest(@NotNull AccountStatus status, @NotBlank @Size(max = 255) String reason) {
}
