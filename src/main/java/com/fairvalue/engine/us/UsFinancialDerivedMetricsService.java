package com.fairvalue.engine.us;

import com.fairvalue.engine.repository.FinancialDerivedMetricsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class UsFinancialDerivedMetricsService {
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.21");

    private final FinancialDerivedMetricsRepository financialDerivedMetricsRepository;

    public UsFinancialDerivedMetricsService(FinancialDerivedMetricsRepository financialDerivedMetricsRepository) {
        this.financialDerivedMetricsRepository = financialDerivedMetricsRepository;
    }

    public int refreshForSecurity(long securityId, List<UsFinancialStandardizedRecord> standardizedRows) {
        List<UsFinancialDerivedMetricRecord> derivedRows = standardizedRows.stream()
                .map(this::toDerivedRecord)
                .toList();
        financialDerivedMetricsRepository.replaceBySecurityId(securityId, derivedRows);
        return derivedRows.size();
    }

    private UsFinancialDerivedMetricRecord toDerivedRecord(UsFinancialStandardizedRecord row) {
        BigDecimal grossMargin = ratio(row.grossProfit(), row.revenue());
        BigDecimal ebitMargin = ratio(row.ebit(), row.revenue());
        BigDecimal fcfMargin = ratio(row.freeCashFlow(), row.revenue());
        BigDecimal roe = ratio(row.netIncome(), row.equity());
        BigDecimal investedCapital = investedCapital(row);
        BigDecimal taxRate = row.taxRateEffective() == null ? DEFAULT_TAX_RATE : row.taxRateEffective();
        BigDecimal nopat = nopat(row, taxRate);
        BigDecimal roic = ratio(nopat, investedCapital);
        BigDecimal roa = ratio(row.netIncome(), row.totalAssets());
        BigDecimal fcfConversion = ratio(row.freeCashFlow(), row.netIncome());
        BigDecimal accrualsRatio = ratio(subtract(row.netIncome(), row.operatingCashFlow()), row.totalAssets());
        BigDecimal netDebtToEbitda = ratio(row.netDebt(), row.ebitda());
        BigDecimal bookValuePerShare = ratio(row.equity(), preferredShares(row));
        BigDecimal epsDiluted = ratio(row.netIncome(), row.dilutedShares());
        BigDecimal ownerEarningsEstimate = subtract(add(row.netIncome(), depreciationEstimate(row)), add(row.capex(), row.sbc()));

        return new UsFinancialDerivedMetricRecord(
                row.securityId(),
                row.periodType(),
                row.fiscalYear(),
                row.fiscalPeriod(),
                row.periodEnd(),
                grossMargin,
                ebitMargin,
                fcfMargin,
                roe,
                roic,
                roa,
                fcfConversion,
                accrualsRatio,
                netDebtToEbitda,
                null,
                null,
                bookValuePerShare,
                epsDiluted,
                ownerEarningsEstimate,
                null
        );
    }

    private BigDecimal nopat(UsFinancialStandardizedRecord row, BigDecimal taxRate) {
        if (row.ebit() != null) {
            return row.ebit().multiply(BigDecimal.ONE.subtract(taxRate));
        }
        // Fall back to reported net income when operating income is unavailable so
        // we still persist a usable ROIC proxy for sparse SEC periods.
        return row.netIncome();
    }

    private BigDecimal investedCapital(UsFinancialStandardizedRecord row) {
        List<BigDecimal> values = new ArrayList<>();
        if (row.equity() != null) {
            values.add(row.equity());
        }
        if (row.totalDebt() != null) {
            values.add(row.totalDebt());
        }
        if (row.leaseLiabilities() != null) {
            values.add(row.leaseLiabilities());
        }
        if (row.minorityInterest() != null) {
            values.add(row.minorityInterest());
        }
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return row.cash() == null ? total : total.subtract(row.cash());
    }

    private BigDecimal depreciationEstimate(UsFinancialStandardizedRecord row) {
        if (row.ebitda() == null || row.ebit() == null) {
            return null;
        }
        return row.ebitda().subtract(row.ebit());
    }

    private BigDecimal preferredShares(UsFinancialStandardizedRecord row) {
        if (row.dilutedShares() != null && row.dilutedShares().compareTo(BigDecimal.ZERO) > 0) {
            return row.dilutedShares();
        }
        if (row.basicShares() != null && row.basicShares().compareTo(BigDecimal.ZERO) > 0) {
            return row.basicShares();
        }
        return null;
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    private BigDecimal subtract(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }
}
