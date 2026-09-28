package com.bankms.controller;

import com.bankms.dto.admin.RateCardResponse;
import com.bankms.dto.branch.BranchSummary;
import com.bankms.dto.deposit.FixedDepositQuote;
import com.bankms.dto.loan.EmiCalculationRequest;
import com.bankms.dto.loan.EmiCalculationResponse;
import com.bankms.service.BranchService;
import com.bankms.service.FixedDepositService;
import com.bankms.service.LoanService;
import com.bankms.service.RateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Public")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final BranchService branchService;
    private final RateService rateService;
    private final LoanService loanService;
    private final FixedDepositService fixedDepositService;

    @Operation(summary = "Active branches (for the registration form)")
    @GetMapping("/branches")
    public List<BranchSummary> branches() {
        return branchService.activeBranches();
    }

    @Operation(summary = "Current interest rates, minimum balances and loan products")
    @GetMapping("/rates")
    public RateCardResponse rates() {
        return rateService.rateCard();
    }

    @Operation(summary = "EMI and amortisation schedule for any principal, rate and tenure")
    @PostMapping("/emi-calculator")
    public EmiCalculationResponse emi(@Valid @RequestBody EmiCalculationRequest request) {
        return loanService.calculate(request);
    }

    @Operation(summary = "Maturity amount for a fixed deposit at today's rate")
    @GetMapping("/fd-quote")
    public FixedDepositQuote fdQuote(
            @RequestParam @DecimalMin("1000.00") @DecimalMax("100000000.00") @Digits(integer = 13, fraction = 2) BigDecimal principal,
            @RequestParam @Min(3) @Max(120) int tenureMonths) {
        return fixedDepositService.quote(principal, tenureMonths);
    }
}
