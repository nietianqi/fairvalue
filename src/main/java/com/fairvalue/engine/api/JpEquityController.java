package com.fairvalue.engine.api;

import com.fairvalue.engine.api.dto.jp.JpEventsResponse;
import com.fairvalue.engine.api.dto.jp.JpFairValueResponse;
import com.fairvalue.engine.api.dto.jp.JpHistoryResponse;
import com.fairvalue.engine.api.dto.jp.JpRecalcRequest;
import com.fairvalue.engine.api.dto.jp.JpRecalcResponse;
import com.fairvalue.engine.api.dto.jp.JpScreenerRequest;
import com.fairvalue.engine.api.dto.jp.JpScreenerResponse;
import com.fairvalue.engine.api.dto.jp.JpStockOverviewResponse;
import com.fairvalue.engine.jp.JpEquityValuationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class JpEquityController {
    private static final String CANONICAL_BASE = "/v1/jp-equities";
    private static final String LEGACY_BASE = "/api/jp/stocks";
    private final JpEquityValuationService jpEquityValuationService;

    public JpEquityController(JpEquityValuationService jpEquityValuationService) {
        this.jpEquityValuationService = jpEquityValuationService;
    }

    @GetMapping(CANONICAL_BASE + "/{code}/overview")
    public JpStockOverviewResponse overview(@PathVariable String code) {
        return jpEquityValuationService.overview(code);
    }

    @GetMapping(CANONICAL_BASE + "/{code}/fair-value")
    public JpFairValueResponse fairValue(@PathVariable String code) {
        return jpEquityValuationService.fairValue(code);
    }

    @GetMapping(CANONICAL_BASE + "/{code}/history")
    public JpHistoryResponse history(@PathVariable String code, @RequestParam(defaultValue = "365") int days) {
        return jpEquityValuationService.history(code, days);
    }

    @PostMapping({CANONICAL_BASE + "/screener", LEGACY_BASE + "/screener"})
    public JpScreenerResponse screener(@Valid @RequestBody(required = false) JpScreenerRequest request) {
        return jpEquityValuationService.screener(request);
    }

    @PostMapping({CANONICAL_BASE + "/recalc", LEGACY_BASE + "/recalc"})
    public JpRecalcResponse recalc(@RequestBody(required = false) JpRecalcRequest request) {
        return jpEquityValuationService.recalc(request);
    }

    @GetMapping(CANONICAL_BASE + "/{code}/events")
    public JpEventsResponse events(@PathVariable String code) {
        return jpEquityValuationService.events(code);
    }

    @GetMapping(LEGACY_BASE + "/{code}/overview")
    public ResponseEntity<Void> legacyOverviewRedirect(@PathVariable String code, HttpServletRequest request) {
        return redirectToCanonical(request, CANONICAL_BASE + "/" + code + "/overview");
    }

    @GetMapping(LEGACY_BASE + "/{code}/fair-value")
    public ResponseEntity<Void> legacyFairValueRedirect(@PathVariable String code, HttpServletRequest request) {
        return redirectToCanonical(request, CANONICAL_BASE + "/" + code + "/fair-value");
    }

    @GetMapping(LEGACY_BASE + "/{code}/history")
    public ResponseEntity<Void> legacyHistoryRedirect(@PathVariable String code, HttpServletRequest request) {
        return redirectToCanonical(request, CANONICAL_BASE + "/" + code + "/history");
    }

    @GetMapping(LEGACY_BASE + "/{code}/events")
    public ResponseEntity<Void> legacyEventsRedirect(@PathVariable String code, HttpServletRequest request) {
        return redirectToCanonical(request, CANONICAL_BASE + "/" + code + "/events");
    }

    private ResponseEntity<Void> redirectToCanonical(HttpServletRequest request, String canonicalPath) {
        String query = request.getQueryString();
        String location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(canonicalPath)
                .query(query)
                .build()
                .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(java.net.URI.create(location))
                .build();
    }
}
