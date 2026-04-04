package com.fairvalue.engine.us;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockFundamentals;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.repository.SecurityMasterRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsSecurityClassificationServiceTest {

    @Test
    void shouldClassifyHospitalAndMedicalServicePlansAsManagedCare() {
        SecurityMasterRepository repository = mock(SecurityMasterRepository.class);
        UsSecurityMasterService masterService = mock(UsSecurityMasterService.class);
        when(masterService.findByTicker("UNH")).thenReturn(Optional.empty());

        UsSecurityClassificationService service = new UsSecurityClassificationService(
                repository,
                masterService,
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        );

        StockSnapshot snapshot = new StockSnapshot(
                Market.US,
                "UNH",
                "USD",
                "UnitedHealth Group",
                "Hospital & Medical Service Plans",
                277.26,
                new StockFundamentals(
                        0.09, 0.07, 0.082, 0.022, 0.22, 18.0, 4.8, 12.5, 0.016, 0.010,
                        -0.06, 0.05, 0.12, 0.91, 0.0, 0.004, 0.18, 8, 0.93, 0.95, 0.06, 0.84, true
                ),
                "sec:2025-10-28"
        );

        UsSecurityMaster classified = service.ensureClassification("UNH", snapshot, null);

        assertThat(classified.companyType()).isEqualTo("managed_care");
        assertThat(classified.sectorTemplate()).isEqualTo("us_managed_care");
    }
}
