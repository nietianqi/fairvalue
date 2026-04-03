package com.fairvalue.engine.platform;

public record ApiPlatformAuthenticatedClient(
        ApiClientRecord client,
        String presentedKey
) {
}
