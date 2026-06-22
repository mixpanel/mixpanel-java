package com.mixpanel.mixpanelapi;

/**
 * Encapsulates service account credentials for server-to-server authentication.
 * <p>
 * <strong>Recommended:</strong> Service account authentication is the preferred method for
 * server-side integrations. Service accounts provide enhanced security by using unique
 * username/secret pairs instead of relying solely on the project token for authentication.
 * </p>
 * <p>
 * Service accounts use a project ID, username, and secret for authentication.
 * This class ensures all required credential fields are provided together.
 * </p>
 * <p>
 * Service account credentials are only used for the /import endpoint (and feature flags).
 * Regular event tracking operations (track, people updates, group updates) use the project
 * token provided in the message payload.
 * </p>
 *
 * @see MixpanelAPI.Builder#credentials(ServiceAccountCredential)
 */
public final class ServiceAccountCredential {
    private final long projectId;
    private final String username;
    private final String secret;

    /**
     * Creates a new ServiceAccountCredential.
     *
     * @param projectId the Mixpanel project ID
     * @param username the service account username
     * @param secret the service account secret
     * @throws IllegalArgumentException if projectId is invalid or username/secret are null or empty
     */
    public ServiceAccountCredential(long projectId, String username, String secret) {
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId must be greater than zero");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username cannot be null or empty");
        }
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("secret cannot be null or empty");
        }

        this.projectId = projectId;
        this.username = username;
        this.secret = secret;
    }

    /**
     * @return the Mixpanel project ID
     */
    public long getProjectId() {
        return projectId;
    }

    /**
     * @return the service account username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the service account secret
     */
    public String getSecret() {
        return secret;
    }
}
