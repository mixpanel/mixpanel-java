package com.mixpanel.mixpanelapi;

/* package */ class Config {
    public static final String BASE_ENDPOINT = "https://api.mixpanel.com";
    public static final int MAX_MESSAGE_SIZE = 50;
    public static final int IMPORT_MAX_MESSAGE_SIZE = 2000;
    
    // Payload size limits for different endpoints
    // When a 413 Payload Too Large error is received, payloads are chunked to these limits
    public static final int IMPORT_MAX_PAYLOAD_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final int TRACK_MAX_PAYLOAD_BYTES = 1 * 1024 * 1024;   // 1 MB
    
    // HTTP status codes
    public static final int HTTP_413_PAYLOAD_TOO_LARGE = 413;
}
