package com.bankms.mapper;

import com.bankms.dto.branch.BranchResponse;
import com.bankms.dto.branch.BranchSummary;
import com.bankms.entity.Branch;
import org.mapstruct.Mapper;

@Mapper
public interface BranchMapper {

    BranchSummary toSummary(Branch branch);

    BranchResponse toResponse(Branch branch);
}
