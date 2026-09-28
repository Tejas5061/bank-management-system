package com.bankms.service;

import com.bankms.audit.AuditAction;
import com.bankms.audit.Audited;
import com.bankms.config.AppProperties;
import com.bankms.dto.branch.BranchRequest;
import com.bankms.dto.branch.BranchResponse;
import com.bankms.dto.branch.BranchSummary;
import com.bankms.dto.common.PageResponse;
import com.bankms.entity.Branch;
import com.bankms.exception.BankException;
import com.bankms.exception.ErrorCode;
import com.bankms.mapper.BranchMapper;
import com.bankms.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branches;
    private final BranchMapper mapper;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public List<BranchSummary> activeBranches() {
        return branches.findAllByActiveTrue(Sort.by("city", "name")).stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> list(Pageable pageable) {
        return PageResponse.of(branches.findAll(pageable), mapper::toResponse);
    }

    /** IFSC = bank prefix + '0' + branch code, e.g. KOSH0000002. */
    @Audited(action = AuditAction.BRANCH_CREATED, entityType = "BRANCH", entityId = "#result.ifsc")
    @Transactional
    public BranchResponse create(BranchRequest request) {
        if (request.code() == null) {
            throw new BankException(ErrorCode.VALIDATION_FAILED, "code is required for a new branch");
        }
        if (branches.existsByCode(request.code())) {
            throw BankException.duplicate("A branch with code " + request.code() + " already exists");
        }
        Branch branch = new Branch();
        branch.setCode(request.code());
        branch.setIfsc(properties.bank().ifscPrefix() + "0" + request.code());
        apply(branch, request);
        return mapper.toResponse(branches.save(branch));
    }

    @Audited(action = AuditAction.BRANCH_UPDATED, entityType = "BRANCH", entityId = "#branchId")
    @Transactional
    public BranchResponse update(Long branchId, BranchRequest request) {
        Branch branch = branches.findById(branchId).orElseThrow(() -> BankException.notFound("Branch", branchId));
        apply(branch, request);
        return mapper.toResponse(branch);
    }

    private static void apply(Branch branch, BranchRequest request) {
        branch.setName(request.name().trim());
        branch.setAddressLine(request.addressLine().trim());
        branch.setCity(request.city().trim());
        branch.setState(request.state().trim());
        branch.setPincode(request.pincode());
        branch.setPhone(request.phone());
        if (request.active() != null) {
            branch.setActive(request.active());
        }
    }
}
