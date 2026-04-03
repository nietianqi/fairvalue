package com.fairvalue.engine.config;

import com.fairvalue.engine.api.dto.ApiEnvelope;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;

@ControllerAdvice(annotations = Controller.class)
public class ApiEnvelopeResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ApiPlatformProperties properties;

    public ApiEnvelopeResponseAdvice(ApiPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        if (!shouldWrap(httpRequest) || body instanceof ApiEnvelope<?>) {
            return body;
        }
        String requestId = (String) httpRequest.getAttribute(ApiPlatformFilter.REQUEST_ID_ATTR);
        Instant timestamp = (Instant) httpRequest.getAttribute(ApiPlatformFilter.REQUEST_TIMESTAMP_ATTR);
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        return ApiEnvelope.success(requestId, timestamp, body);
    }

    private boolean shouldWrap(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/v1/")) {
            return false;
        }
        return properties.shouldWrap(request.getHeader(properties.getEnvelopeHeader()));
    }
}
