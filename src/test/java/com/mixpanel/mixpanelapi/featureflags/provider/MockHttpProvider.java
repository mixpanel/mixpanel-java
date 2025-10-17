package com.mixpanel.mixpanelapi.featureflags.provider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class providing HTTP mocking infrastructure for testing providers.
 * This class provides URL-pattern-based HTTP response mocking.
 * <p>
 * Used by test subclasses to override httpGet() behavior without making real network calls.
 * </p>
 */
public class MockHttpProvider {
    private final Map<String, String> urlToResponseMap = new HashMap<>();
    private IOException mockException;

    /**
     * Set a mock response for a specific URL pattern.
     * The URL pattern can be a substring that the actual URL should contain.
     *
     * @param urlPattern the URL pattern to match (substring match)
     * @param response the response to return for matching URLs
     */
    public void setMockResponse(String urlPattern, String response) {
        this.urlToResponseMap.put(urlPattern, response);
        this.mockException = null;
    }

    /**
     * Set a mock exception to be thrown on any HTTP call.
     * This simulates network failures or other HTTP errors.
     *
     * @param exception the exception to throw
     */
    public void setMockException(IOException exception) {
        this.mockException = exception;
        this.urlToResponseMap.clear();
    }

    /**
     * Mock implementation of httpGet that returns configured responses.
     * <p>
     * This method:
     * <ul>
     *   <li>Throws the configured exception if one is set</li>
     *   <li>Returns a matching mock response based on URL pattern</li>
     *   <li>Throws an IOException if no mock is configured</li>
     * </ul>
     * </p>
     *
     * @param urlString the URL being requested
     * @return the mock response for this URL
     * @throws IOException if an exception is configured or no mock found
     */
    public String mockHttpGet(String urlString) throws IOException {
        if (mockException != null) {
            throw mockException;
        }

        // Try to find a matching URL pattern
        for (Map.Entry<String, String> entry : urlToResponseMap.entrySet()) {
            if (urlString.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // No mock found - throw exception to simulate network error
        throw new IOException("No mock response configured for URL: " + urlString);
    }
}
