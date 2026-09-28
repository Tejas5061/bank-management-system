package com.bankms.mapper;

import com.bankms.dto.admin.AccountPolicyResponse;
import com.bankms.dto.admin.AuditLogResponse;
import com.bankms.dto.admin.EmployeeResponse;
import com.bankms.dto.admin.LoanProductResponse;
import com.bankms.dto.notification.NotificationResponse;
import com.bankms.entity.AccountPolicy;
import com.bankms.entity.AuditLog;
import com.bankms.entity.Employee;
import com.bankms.entity.LoanProduct;
import com.bankms.entity.Notification;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

/** Small, flat mappings for the admin console and notifications. */
@Mapper
public interface AdminMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "branchCode", source = "branch.code")
    @Mapping(target = "branchName", source = "branch.name")
    @Mapping(target = "enabled", source = "user.enabled")
    @Mapping(target = "locked", expression = "java(employee.getUser().isLocked(now))")
    @Mapping(target = "lastLoginAt", source = "user.lastLoginAt")
    EmployeeResponse toEmployee(Employee employee, @Context Instant now);

    AccountPolicyResponse toPolicy(AccountPolicy policy);

    LoanProductResponse toLoanProduct(LoanProduct product);

    AuditLogResponse toAuditLog(AuditLog log);

    NotificationResponse toNotification(Notification notification);
}
