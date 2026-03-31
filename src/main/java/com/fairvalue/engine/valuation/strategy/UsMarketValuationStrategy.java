package com.fairvalue.engine.valuation.strategy;

import com.fairvalue.engine.domain.Market;
import com.fairvalue.engine.domain.StockSnapshot;
import com.fairvalue.engine.us.UsConfiguredMethodValuation;
import com.fairvalue.engine.us.UsConfiguredValuationModelsService;
import com.fairvalue.engine.us.UsConfiguredValuationResult;
import com.fairvalue.engine.us.UsSecurityClassificationService;
import com.fairvalue.engine.us.UsSecurityMaster;
import com.fairvalue.engine.us.UsSecClient;
import com.fairvalue.engine.valuation.MarketComputation;
import com.fairvalue.engine.valuation.ModelValuation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UsMarketValuationStrategy implements MarketValuationStrategy {
    private final UsConfiguredValuationModelsService configuredValuationModelsService;
    private final UsSecurityClassificationService usSecurityClassificationService;
    private final UsSecClient usSecClient;

    public UsMarketValuationStrategy(
            UsConfiguredValuationModelsService configuredValuationModelsService,
            UsSecurityClassificationService usSecurityClassificationService,
            UsSecClient usSecClient
    ) {
        this.configuredValuationModelsService = configuredValuationModelsService;
        this.usSecurityClassificationService = usSecurityClassificationService;
        this.usSecClient = usSecClient;
    }

    @Override
    public Market market() {
        return Market.US;
    }

    @Override
    public MarketComputation evaluate(StockSnapshot snapshot) {
        UsSecClient.UsSecProfile profile = usSecClient.fetchProfile(snapshot.symbol()).orElse(null);
        UsSecurityMaster classified = usSecurityClassificationService.ensureClassification(snapshot.symbol(), snapshot, profile);
        UsConfiguredValuationResult configured = configuredValuationModelsService.evaluate(snapshot, classified, Map.of());

        List<ModelValuation> models = configured.methods().stream()
                .map(this::toModelValuation)
                .toList();

        return new MarketComputation(
                models,
                configured.adjustments(),
                configured.riskFlags(),
                configured.drivers(),
                configured.confidenceBase(),
                configured.methodology(),
                configured.modelSelectionReason()
        );
    }

    private ModelValuation toModelValuation(UsConfiguredMethodValuation method) {
        return new ModelValuation(
                method.method(),
                MathSupport.round(method.baseValue()),
                MathSupport.round(method.weight()),
                method.rationale()
        );
    }
}
