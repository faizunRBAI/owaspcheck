package com.enterprise.idp.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Uniform error payload returned by the API. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
