package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.BaseFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.RemoteFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.util.VersionUtil;
import com.mixpanel.mixpanelapi.internal.JsonSerializer;
import com.mixpanel.mixpanelapi.internal.OrgJsonSerializer;

/**
 * Simple interface to the Mixpanel tracking API, intended for use in
 * server-side applications. Users are encouraged to review our Javascript
 * API for reporting user events in web applications, and our Android API
 * for use in Android mobile applications.
 *
 * The Java API doesn't provide or assume any threading model, and is designed
 * such that recording events and sending them can be easily separated.
 *
 *
 */
public class MixpanelAPI implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(MixpanelAPI.class.getName());
    private static final int BUFFER_SIZE = 256; // Small, we expect small responses.

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    protected final String mEventsEndpoint;
    protected final String mPeopleEndpoint;
    protected final String mGroupsEndpoint;
    protected final String mImportEndpoint;
    protected final boolean mUseGzipCompression;
    protected final Integer mConnectTimeout;
    protected final Integer mReadTimeout;
    protected final LocalFlagsProvider mLocalFlags;
    protected final RemoteFlagsProvider mRemoteFlags;
    protected final JsonSerializer mJsonSerializer;
    protected final OrgJsonSerializer mDefaultJsonSerializer;

    /**
     * Constructs a MixpanelAPI object associated with the production, Mixpanel services.
     */
    public MixpanelAPI() {
        this(false);
    }

    /**
     * Constructs a MixpanelAPI object associated with the production, Mixpanel services.
     *
     * @param useGzipCompression whether to use gzip compression for network requests
     */
    public MixpanelAPI(boolean useGzipCompression) {
        this(null, null, null, null, useGzipCompression, null, null, null, null, null);
    }

    /**
     * Constructs a MixpanelAPI object with local feature flags evaluation.
     *
     * @param localFlagsConfig configuration for local feature flags evaluation
     */
    public MixpanelAPI(LocalFlagsConfig localFlagsConfig) {
        this(localFlagsConfig, null);
    }

    /**
     * Constructs a MixpanelAPI object with remote feature flags evaluation.
     *
     * @param remoteFlagsConfig configuration for remote feature flags evaluation
     */
    public MixpanelAPI(RemoteFlagsConfig remoteFlagsConfig) {
        this(null, remoteFlagsConfig);
    }

    /**
     * Private constructor for feature flags configurations.
     * Initializes with default endpoints and no gzip compression.
     *
     * @param localFlagsConfig configuration for local feature flags evaluation (can be null)
     * @param remoteFlagsConfig configuration for remote feature flags evaluation (can be null)
     */
    private MixpanelAPI(LocalFlagsConfig localFlagsConfig, RemoteFlagsConfig remoteFlagsConfig) {
        this(null, null, null, null, false, localFlagsConfig, remoteFlagsConfig, null, null, null);
    }

    /**
     * Create a MixpanelAPI associated with custom URLS for events and people updates.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint) {
        this(eventsEndpoint, peopleEndpoint, null, null, false, null, null, null, null, null);
    }

    /**
     * Create a MixpanelAPI associated with custom URLS for the Mixpanel service.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint, String groupsEndpoint) {
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, null, false, null, null, null, null, null);
    }

    /**
     * Create a MixpanelAPI associated with custom URLS for the Mixpanel service.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages
     * @param importEndpoint a URL that will accept Mixpanel import messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint, String groupsEndpoint, String importEndpoint) {
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, importEndpoint, false, null, null, null, null, null);
    }

    /**
     * Create a MixpanelAPI associated with custom URLS for the Mixpanel service.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages
     * @param importEndpoint a URL that will accept Mixpanel import messages
     * @param useGzipCompression whether to use gzip compression for network requests
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint, String groupsEndpoint, String importEndpoint, boolean useGzipCompression) {
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, importEndpoint, useGzipCompression, null, null, null, null, null);
    }

    /**
     * Constructs a MixpanelAPI object using a builder.
     *
     * @param builder the Builder instance containing configuration
     */
    private MixpanelAPI(Builder builder) {
        this(
            builder.eventsEndpoint, 
            builder.peopleEndpoint, 
            builder.groupsEndpoint, 
            builder.importEndpoint, 
            builder.useGzipCompression,
            builder.flagsConfig instanceof LocalFlagsConfig ? (LocalFlagsConfig) builder.flagsConfig : null,
            builder.flagsConfig instanceof RemoteFlagsConfig ? (RemoteFlagsConfig) builder.flagsConfig : null,
            builder.jsonSerializer,
            builder.connectTimeout,
            builder.readTimeout
        );
    }

    /**
     * Main private constructor used by all other constructors.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages (null uses default)
     * @param peopleEndpoint a URL that will accept Mixpanel people messages (null uses default)
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages (null uses default)
     * @param importEndpoint a URL that will accept Mixpanel import messages (null uses default)
     * @param useGzipCompression whether to use gzip compression for network requests
     * @param localFlagsConfig configuration for local feature flags
     * @param remoteFlagsConfig configuration for remote feature flags
     * @param jsonSerializer custom JSON serializer (null uses default)
     */
    private MixpanelAPI(
        String eventsEndpoint, 
        String peopleEndpoint, 
        String groupsEndpoint, 
        String importEndpoint, 
        boolean useGzipCompression, 
        LocalFlagsConfig localFlagsConfig, 
        RemoteFlagsConfig remoteFlagsConfig,
        JsonSerializer jsonSerializer,
        Integer connectTimeout,
        Integer readTimeout
    ) {
        mEventsEndpoint = eventsEndpoint != null ? eventsEndpoint : Config.BASE_ENDPOINT + "/track";
        mPeopleEndpoint = peopleEndpoint != null ? peopleEndpoint : Config.BASE_ENDPOINT + "/engage";
        mGroupsEndpoint = groupsEndpoint != null ? groupsEndpoint : Config.BASE_ENDPOINT + "/groups";
        mImportEndpoint = importEndpoint != null ? importEndpoint : Config.BASE_ENDPOINT + "/import";
        mUseGzipCompression = useGzipCompression;
        mConnectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT_MILLIS;
        mReadTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT_MILLIS;
        mDefaultJsonSerializer = new OrgJsonSerializer();
        if (jsonSerializer != null) {
            logger.log(Level.INFO, "Custom JsonSerializer provided: " + jsonSerializer.getClass().getName());
            mJsonSerializer = jsonSerializer;
        } else {
            mJsonSerializer = mDefaultJsonSerializer;
        }

        if (localFlagsConfig != null) {
            EventSender eventSender = createEventSender(localFlagsConfig, this);
            mLocalFlags = new LocalFlagsProvider(localFlagsConfig, VersionUtil.getVersion(), eventSender);
            mRemoteFlags = null;
        } else if (remoteFlagsConfig != null) {
            EventSender eventSender = createEventSender(remoteFlagsConfig, this);
            mLocalFlags = null;
            mRemoteFlags = new RemoteFlagsProvider(remoteFlagsConfig, VersionUtil.getVersion(), eventSender);
        } else {
            mLocalFlags = null;
            mRemoteFlags = null;
        }
    }

    /**
     * Sends a single message to Mixpanel servers.
     *
     * Each call to sendMessage results in a blocking call to remote Mixpanel servers.
     * To send multiple messages at once, see #{@link #deliver(ClientDelivery)}
     *
     * @param message A JSONObject formatted by #{@link MessageBuilder}
     * @throws MixpanelMessageException if the given JSONObject is not (apparently) a Mixpanel message. This is a RuntimeException, callers should take care to submit only correctly formatted messages.
     * @throws IOException if
     */
    public void sendMessage(JSONObject message)
        throws MixpanelMessageException, IOException {
        ClientDelivery delivery = new ClientDelivery();
        delivery.addMessage(message);
        deliver(delivery);
    }

    /**
     * Sends a ClientDelivery full of messages to Mixpanel's servers.
     *
     * This call will block, possibly for a long time.
     * @param toSend
     * @throws IOException
     * @see ClientDelivery
     */
    public void deliver(ClientDelivery toSend) throws IOException {
        deliver(toSend, false);
    }

    /**
     * Attempts to send a given delivery to the Mixpanel servers. Will block,
     * possibly on multiple server requests. For most applications, this method
     * should be called in a separate thread or in a queue consumer.
     *
     * @param toSend a ClientDelivery containing a number of Mixpanel messages
     * @throws IOException
     * @see ClientDelivery
     */
    public void deliver(ClientDelivery toSend, boolean useIpAddress) throws IOException {
        String ipParameter = "ip=0";
        if (useIpAddress) {
            ipParameter = "ip=1";
        }

        String eventsUrl = mEventsEndpoint + "?" + ipParameter;
        List<JSONObject> events = toSend.getEventsMessages();
        sendMessages(events, eventsUrl);

        String peopleUrl = mPeopleEndpoint + "?" + ipParameter;
        List<JSONObject> people = toSend.getPeopleMessages();
        sendMessages(people, peopleUrl);

        String groupsUrl = mGroupsEndpoint + "?" + ipParameter;
        List<JSONObject> groupMessages = toSend.getGroupMessages();
        sendMessages(groupMessages, groupsUrl);

        // Handle import messages - use strict mode and extract token for auth
        List<JSONObject> importMessages = toSend.getImportMessages();
        if (importMessages.size() > 0) {
            String importUrl = mImportEndpoint + "?strict=1";
            sendImportMessages(importMessages, importUrl);
        }
    }

    /**
     * apply Base64 encoding followed by URL encoding
     *
     * @param dataString JSON formatted string
     * @return encoded string for <b>data</b> parameter in API call
     * @throws NullPointerException If {@code dataString} is {@code null}
     */
    protected String encodeDataString(String dataString) {
        try {
            byte[] utf8data = dataString.getBytes("utf-8");
            String base64data = new String(Base64Coder.encode(utf8data));
            return URLEncoder.encode(base64data, "utf8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Mixpanel library requires utf-8 support", e);
        }
    }

    /**
     * Package scope for mocking purposes
     */
    /* package */ boolean sendData(String dataString, String endpointUrl) throws IOException {
        URL endpoint = new URL(endpointUrl);
        URLConnection conn = endpoint.openConnection();
        conn.setReadTimeout(mReadTimeout);
        conn.setConnectTimeout(mConnectTimeout);
        conn.setDoOutput(true);

        byte[] dataToSend;
        if (mUseGzipCompression) {
            // Use gzip compression
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf8");
            conn.setRequestProperty("Content-Encoding", "gzip");

            String encodedData = encodeDataString(dataString);
            String encodedQuery = "data=" + encodedData;

            // Compress the data
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            GZIPOutputStream gzipStream = null;
            try {
                gzipStream = new GZIPOutputStream(byteStream);
                gzipStream.write(encodedQuery.getBytes("utf-8"));
                gzipStream.finish();
                dataToSend = byteStream.toByteArray();
            } finally {
                if (gzipStream != null) {
                    try {
                        gzipStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } else {
            // No compression
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf8");
            String encodedData = encodeDataString(dataString);
            String encodedQuery = "data=" + encodedData;
            dataToSend = encodedQuery.getBytes("utf-8");
        }

        OutputStream postStream = null;
        try {
            postStream = conn.getOutputStream();
            postStream.write(dataToSend);
        } finally {
            if (postStream != null) {
                try {
                    postStream.close();
                } catch (IOException e) {
                    // ignore, in case we've already thrown
                }
            }
        }

        InputStream responseStream = null;
        String response = null;
        try {
            responseStream = conn.getInputStream();
            response = slurp(responseStream);
        } finally {
            if (responseStream != null) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    // ignore, in case we've already thrown
                }
            }
        }

        return ((response != null) && response.equals("1"));
    }

    private void sendMessages(List<JSONObject> messages, String endpointUrl) throws IOException {
        for (int i = 0; i < messages.size(); i += Config.MAX_MESSAGE_SIZE) {
            int endIndex = i + Config.MAX_MESSAGE_SIZE;
            endIndex = Math.min(endIndex, messages.size());
            List<JSONObject> batch = messages.subList(i, endIndex);

            if (batch.size() > 0) {
                String messagesString = dataString(batch);
                boolean accepted = sendData(messagesString, endpointUrl);

                if (! accepted) {
                    throw new MixpanelServerException("Server refused to accept messages, they may be malformed.", batch);
                }
            }
        }
    }

    private void sendImportMessages(List<JSONObject> messages, String endpointUrl) throws IOException {
        // Extract token from first message for authentication
        // If token is missing, we'll still attempt to send and let the server reject it
        String token = "";
        if (messages.size() > 0) {
            try {
                JSONObject firstMessage = messages.get(0);
                if (firstMessage.has("properties")) {
                    JSONObject properties = firstMessage.getJSONObject("properties");
                    if (properties.has("token")) {
                        token = properties.getString("token");
                    }
                }
            } catch (JSONException e) {
                // Malformed message - continue with empty token and let server reject it
            }
        }

        // Send messages in batches (max 2000 per batch for /import)
        // If token is empty, the server will reject with 401 Unauthorized
        for (int i = 0; i < messages.size(); i += Config.IMPORT_MAX_MESSAGE_SIZE) {
            int endIndex = i + Config.IMPORT_MAX_MESSAGE_SIZE;
            endIndex = Math.min(endIndex, messages.size());
            List<JSONObject> batch = messages.subList(i, endIndex);

            if (batch.size() > 0) {
                // dataString now uses high-performance Jackson serialization when available
                String messagesString = dataString(batch);
                boolean accepted = sendImportData(messagesString, endpointUrl, token);

                if (! accepted) {
                    throw new MixpanelServerException("Server refused to accept import messages, they may be malformed.", batch);
                }
            }
        }
    }

    private String dataString(List<JSONObject> messages) {
        try {
            return mJsonSerializer.serializeArray(messages);
        } catch (IOException e) {
            // Fallback to original implementation if serialization fails
            logger.log(Level.WARNING, "JSON serialization failed unexpectedly; falling back to org.json implementation", e);
            return mDefaultJsonSerializer.serializeArray(messages);
        }
    }

    /**
     * Sends import data to the /import endpoint with Basic Auth using the project token.
     * The /import endpoint requires:
     * - JSON content type (not URL-encoded like /track)
     * - Basic authentication with token as username and empty password
     * - strict=1 parameter for validation
     *
     * @param dataString JSON array of events to import
     * @param endpointUrl The import endpoint URL
     * @param token The project token for Basic Auth
     * @return true if the server accepted the data
     * @throws IOException if there's a network error
     */
    /* package */ boolean sendImportData(String dataString, String endpointUrl, String token) throws IOException {
        URL endpoint = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        conn.setReadTimeout(mReadTimeout);
        conn.setConnectTimeout(mConnectTimeout);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        // Add Basic Auth header: username is token, password is empty
        try {
            String authString = token + ":";
            byte[] authBytes = authString.getBytes("utf-8");
            String base64Auth = new String(Base64Coder.encode(authBytes));
            conn.setRequestProperty("Authorization", "Basic " + base64Auth);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Mixpanel library requires utf-8 support", e);
        }

        byte[] dataToSend;
        if (mUseGzipCompression) {
            // Use gzip compression
            conn.setRequestProperty("Content-Encoding", "gzip");

            // Compress the data
            java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
            GZIPOutputStream gzipStream = null;
            try {
                gzipStream = new GZIPOutputStream(byteStream);
                gzipStream.write(dataString.getBytes("utf-8"));
                gzipStream.finish();
                dataToSend = byteStream.toByteArray();
            } finally {
                if (gzipStream != null) {
                    try {
                        gzipStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        } else {
            // No compression
            dataToSend = dataString.getBytes("utf-8");
        }

        OutputStream postStream = null;
        try {
            postStream = conn.getOutputStream();
            postStream.write(dataToSend);
        } finally {
            if (postStream != null) {
                try {
                    postStream.close();
                } catch (IOException e) {
                    // ignore, in case we've already thrown
                }
            }
        }

        InputStream responseStream = null;
        String response = null;
        try {
            responseStream = conn.getInputStream();
            response = slurp(responseStream);
        } catch (IOException e) {
            // HTTP error codes (401, 400, etc.) throw IOException when calling getInputStream()
            // Check if it's an HTTP error and read the error stream for details
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                try {
                    slurp(errorStream);
                    errorStream.close();
                    // Return false to indicate rejection, which will throw MixpanelServerException
                    return false;
                } catch (IOException ignored) {
                    // If we can't read the error stream, just let the original exception propagate
                }
            }
            // Network error or other IOException - propagate it
            throw e;
        } finally {
            if (responseStream != null) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    // ignore, in case we've already thrown
                }
            }
        }

        // Import endpoint returns JSON like {"code":200,"status":"OK","num_records_imported":N}
        if (response == null) {
            return false;
        }

        // Parse JSON response
        try {
            JSONObject jsonResponse = new JSONObject(response);

            // Check for {"status":"OK"} and {"code":200}
            boolean statusOk = jsonResponse.has("status") && "OK".equals(jsonResponse.getString("status"));
            boolean codeOk = jsonResponse.has("code") && jsonResponse.getInt("code") == 200;

            return statusOk && codeOk;
        } catch (JSONException e) {
            // Not valid JSON or missing expected fields
            return false;
        }
    }

    private String slurp(InputStream in) throws IOException {
        final StringBuilder out = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(in, "utf8");

        char[] readBuffer = new char[BUFFER_SIZE];
        int readCount = 0;
        do {
            readCount = reader.read(readBuffer);
            if (readCount > 0) {
                out.append(readBuffer, 0, readCount);
            }
        } while(readCount != -1);

        return out.toString();
    }

    /**
     * Gets the local flags provider for evaluating feature flags locally.
     *
     * @return the LocalFlagsProvider, or null if not configured
     */
    public LocalFlagsProvider getLocalFlags() {
        return mLocalFlags;
    }

    /**
     * Gets the remote flags provider for evaluating feature flags remotely.
     *
     * @return the RemoteFlagsProvider, or null if not configured
     */
    public RemoteFlagsProvider getRemoteFlags() {
        return mRemoteFlags;
    }

    /**
     * Creates an EventSender that uses the provided MixpanelAPI instance for sending events.
     * This is shared by both local and remote flag evaluation modes.
     */
    private static EventSender createEventSender(BaseFlagsConfig config, MixpanelAPI api) {
        final MessageBuilder builder = new MessageBuilder(config.getProjectToken());

        return (distinctId, eventName, properties) -> {
            try {
                JSONObject event = builder.event(distinctId, eventName, properties);
                api.sendMessage(event);
            } catch (IOException e) {
                // Silently fail - exposure tracking should not break flag evaluation
            }
        };
    }

    /**
     * Closes this MixpanelAPI instance and releases any resources held by the flags providers.
     * This method should be called when the MixpanelAPI instance is no longer needed.
     */
    @Override
    public void close() {
        if (mLocalFlags != null) {
            mLocalFlags.close();
        }
    }

    /**
     * Builder class for constructing a MixpanelAPI instance with optional configuration.
     * 
     * <p>The Builder pattern provides a flexible way to configure MixpanelAPI with various
     * options including custom endpoints, gzip compression, feature flags, and JSON serializers.</p>
     *
     * @since 1.6.1
     */
    public static class Builder {
        private String eventsEndpoint;
        private String peopleEndpoint;
        private String groupsEndpoint;
        private String importEndpoint;
        private boolean useGzipCompression;
        private BaseFlagsConfig flagsConfig;
        private JsonSerializer jsonSerializer;
        private Integer connectTimeout;
        private Integer readTimeout;

        /**
         * Sets the endpoint URL for Mixpanel events messages.
         *
         * @param eventsEndpoint the URL that will accept Mixpanel events messages
         * @return this Builder instance for method chaining
         */
        public Builder eventsEndpoint(String eventsEndpoint) {
            this.eventsEndpoint = eventsEndpoint;
            return this;
        }

        /**
         * Sets the endpoint URL for Mixpanel people messages.
         *
         * @param peopleEndpoint the URL that will accept Mixpanel people messages
         * @return this Builder instance for method chaining
         */
        public Builder peopleEndpoint(String peopleEndpoint) {
            this.peopleEndpoint = peopleEndpoint;
            return this;
        }

        /**
         * Sets the endpoint URL for Mixpanel groups messages.
         *
         * @param groupsEndpoint the URL that will accept Mixpanel groups messages
         * @return this Builder instance for method chaining
         */
        public Builder groupsEndpoint(String groupsEndpoint) {
            this.groupsEndpoint = groupsEndpoint;
            return this;
        }

        /**
         * Sets the endpoint URL for Mixpanel import messages.
         *
         * @param importEndpoint the URL that will accept Mixpanel import messages
         * @return this Builder instance for method chaining
         */
        public Builder importEndpoint(String importEndpoint) {
            this.importEndpoint = importEndpoint;
            return this;
        }

        /**
         * Sets whether to use gzip compression for network requests.
         *
         * @param useGzipCompression true to enable gzip compression, false otherwise
         * @return this Builder instance for method chaining
         */
        public Builder useGzipCompression(boolean useGzipCompression) {
            this.useGzipCompression = useGzipCompression;
            return this;
        }

        /**
         * Sets the configuration for feature flags evaluation.
         * Accepts either LocalFlagsConfig or RemoteFlagsConfig.
         *
         * @param flagsConfig configuration for feature flags evaluation
         * @return this Builder instance for method chaining
         */
        public Builder flagsConfig(BaseFlagsConfig flagsConfig) {
            this.flagsConfig = flagsConfig;
            return this;
        }

        /**
         * Sets a custom JSON serializer for message serialization.
         *
         * @param jsonSerializer custom JSON serializer implementation
         * @return this Builder instance for method chaining
         */
        public Builder jsonSerializer(JsonSerializer jsonSerializer) {
            this.jsonSerializer = jsonSerializer;
            return this;
        }

        /**
         * Sets the connect timeout for Mixpanel network requests
         *
         * @param connectTimeoutInMillis connection timeout in milliseconds.
         *                               Value must be >= 0.
         *                               0 indicates indefinite (no) timeout.
         * @return this Builder instance for method chaining
         */
        public Builder connectTimeout(int connectTimeoutInMillis) {
            if (connectTimeoutInMillis >= 0) {
                this.connectTimeout = connectTimeoutInMillis;
            }
            return this;
        }

        /**
         * Sets the read timeout for Mixpanel network requests
         *
         * @param readTimeoutInMillis read timeout in milliseconds.
         *                            Value must be >= 0.
         *                            0 indicates indefinite (no) timeout.
         * @return this Builder instance for method chaining
         */
        public Builder readTimeout(int readTimeoutInMillis) {
            if (readTimeoutInMillis >= 0) {
                this.readTimeout = readTimeoutInMillis;
            }
            return this;
        }

        /**
         * Builds and returns a new MixpanelAPI instance with the configured settings.
         *
         * @return a new MixpanelAPI instance
         */
        public MixpanelAPI build() {
            return new MixpanelAPI(this);
        }
    }

}
