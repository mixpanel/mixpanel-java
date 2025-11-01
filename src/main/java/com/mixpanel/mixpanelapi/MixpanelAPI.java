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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.BaseFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.RemoteFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.util.VersionUtil;

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

    private static final int BUFFER_SIZE = 256; // Small, we expect small responses.

    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int READ_TIMEOUT_MILLIS = 10000;

    // Instance fields for customizable timeouts (per-instance control)
    private int mConnectTimeoutMillis = CONNECT_TIMEOUT_MILLIS;
    private int mReadTimeoutMillis = READ_TIMEOUT_MILLIS;
    private boolean mStrictImportMode = true;

    protected final String mEventsEndpoint;
    protected final String mPeopleEndpoint;
    protected final String mGroupsEndpoint;
    protected final String mImportEndpoint;
    protected final boolean mUseGzipCompression;
    protected final LocalFlagsProvider mLocalFlags;
    protected final RemoteFlagsProvider mRemoteFlags;
    protected final int mImportMaxMessageSize;

    // Track the last response from import endpoint for error logging
    protected String mLastResponseBody;
    protected int mLastStatusCode;

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
        this(Config.BASE_ENDPOINT + "/track", Config.BASE_ENDPOINT + "/engage", Config.BASE_ENDPOINT + "/groups", Config.BASE_ENDPOINT + "/import", useGzipCompression, Config.IMPORT_MAX_MESSAGE_SIZE, null, null);
    }

    /**
     * Constructs a MixpanelAPI object associated with the production, Mixpanel services.
     *
     * @param importMaxMessageSize custom batch size for /import endpoint (must be between 1 and 2000)
     */
    public MixpanelAPI(int importMaxMessageSize) {
        this(false, importMaxMessageSize);
    }

    /**
     * Constructs a MixpanelAPI object associated with the production, Mixpanel services.
     *
     * @param useGzipCompression whether to use gzip compression for network requests
     * @param importMaxMessageSize custom batch size for /import endpoint (must be between 1 and 2000)
     */
    public MixpanelAPI(boolean useGzipCompression, int importMaxMessageSize) {
        this(Config.BASE_ENDPOINT + "/track", Config.BASE_ENDPOINT + "/engage", Config.BASE_ENDPOINT + "/groups", Config.BASE_ENDPOINT + "/import", useGzipCompression, importMaxMessageSize, null, null);
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
        mEventsEndpoint = Config.BASE_ENDPOINT + "/track";
        mPeopleEndpoint = Config.BASE_ENDPOINT + "/engage";
        mGroupsEndpoint = Config.BASE_ENDPOINT + "/groups";
        mImportEndpoint = Config.BASE_ENDPOINT + "/import";
        mUseGzipCompression = false;
        mImportMaxMessageSize = Config.IMPORT_MAX_MESSAGE_SIZE;

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
     * Create a MixpanelAPI associated with custom URLS for events and people updates.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint) {
        this(eventsEndpoint, peopleEndpoint, Config.BASE_ENDPOINT + "/groups", Config.BASE_ENDPOINT + "/import", false, Config.IMPORT_MAX_MESSAGE_SIZE, null, null);
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
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, Config.BASE_ENDPOINT + "/import", false, Config.IMPORT_MAX_MESSAGE_SIZE, null, null);
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
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, importEndpoint, false, Config.IMPORT_MAX_MESSAGE_SIZE, null, null);
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
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, importEndpoint, useGzipCompression, Config.IMPORT_MAX_MESSAGE_SIZE, null, null);
    }

    /**
     * Main constructor used by all other constructors.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages
     * @param importEndpoint a URL that will accept Mixpanel import messages
     * @param useGzipCompression whether to use gzip compression for network requests
     * @param importMaxMessageSize custom batch size for /import endpoint (must be between 1 and 2000)
     * @param localFlags optional LocalFlagsProvider for local feature flags (can be null)
     * @param remoteFlags optional RemoteFlagsProvider for remote feature flags (can be null)
     */
    /* package */ MixpanelAPI(String eventsEndpoint, String peopleEndpoint, String groupsEndpoint, String importEndpoint, boolean useGzipCompression, int importMaxMessageSize, LocalFlagsProvider localFlags, RemoteFlagsProvider remoteFlags) {
        mEventsEndpoint = eventsEndpoint;
        mPeopleEndpoint = peopleEndpoint;
        mGroupsEndpoint = groupsEndpoint;
        mImportEndpoint = importEndpoint;
        mUseGzipCompression = useGzipCompression;
        mImportMaxMessageSize = Math.max(1, Math.min(importMaxMessageSize, 2000));
        mLocalFlags = localFlags;
        mRemoteFlags = remoteFlags;
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
            String strictParam = mStrictImportMode ? "1" : "0";
            String importUrl = mImportEndpoint + "?strict=" + strictParam;
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
     * Container for HTTP response information including status code.
     * Used to communicate both success/failure and the specific HTTP status code.
     */
    /* package */ static class HttpStatusResponse {
        public final boolean success;
        public final int statusCode;

        public HttpStatusResponse(boolean success, int statusCode) {
            this.success = success;
            this.statusCode = statusCode;
        }
    }

    /**
     * Package scope for mocking purposes.
     * 
     * Sends data to an endpoint and returns both success status and HTTP status code.
     * This allows callers to detect specific error conditions like 413 Payload Too Large.
     * 
     * When a 413 error is received, the caller should split the payload into smaller chunks
     * using PayloadChunker and retry each chunk individually.
     */
    /* package */ HttpStatusResponse sendData(String dataString, String endpointUrl) throws IOException {
        URL endpoint = new URL(endpointUrl);
        URLConnection conn = endpoint.openConnection();
        conn.setReadTimeout(mReadTimeoutMillis);
        conn.setConnectTimeout(mConnectTimeoutMillis);
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

        // For HttpURLConnection, we need to handle status codes
        int statusCode = 0;
        if (conn instanceof HttpURLConnection) {
            try {
                statusCode = ((HttpURLConnection) conn).getResponseCode();
            } catch (IOException e) {
                // If we can't get the status code, return failure
                return new HttpStatusResponse(false, 0);
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

        boolean accepted = ((response != null) && response.equals("1"));
        return new HttpStatusResponse(accepted, statusCode);
    }

    private void sendMessages(List<JSONObject> messages, String endpointUrl) throws IOException {
        for (int i = 0; i < messages.size(); i += Config.MAX_MESSAGE_SIZE) {
            int endIndex = i + Config.MAX_MESSAGE_SIZE;
            endIndex = Math.min(endIndex, messages.size());
            List<JSONObject> batch = messages.subList(i, endIndex);

            if (batch.size() > 0) {
                String messagesString = dataString(batch);
                HttpStatusResponse response = sendData(messagesString, endpointUrl);

                if (!response.success) {
                    // Check if we got a 413 Payload Too Large error
                    if (response.statusCode == Config.HTTP_413_PAYLOAD_TOO_LARGE) {
                        // Retry with chunked payloads (only once)
                        sendMessagesChunked(batch, endpointUrl);
                    } else {
                        throw new MixpanelServerException("Server refused to accept messages, they may be malformed.", batch);
                    }
                }
            }
        }
    }

    /**
     * Sends messages by chunking the payload to handle 413 Payload Too Large errors.
     * This is a retry mechanism that only happens once when the initial send returns 413.
     * 
     * The messages are split into smaller chunks using PayloadChunker, with each chunk
     * sized to be under the track endpoint's limit (1 MB). Each chunk is sent independently,
     * and we do NOT retry chunked sends that fail - only the initial payload gets one retry.
     * 
     * @param batch the batch of messages to send in chunks
     * @param endpointUrl the endpoint URL
     * @throws IOException if there's a network error
     * @throws MixpanelServerException if any chunk is rejected by the server
     */
    private void sendMessagesChunked(List<JSONObject> batch, String endpointUrl) throws IOException {
        String originalPayload = dataString(batch);
        
        try {
            // Split the payload into chunks under the track endpoint's 1MB limit
            // Size limits are based on uncompressed data (server limits apply to uncompressed payloads)
            List<String> chunks = PayloadChunker.chunkJsonArray(originalPayload, Config.TRACK_MAX_PAYLOAD_BYTES);
            
            for (String chunk : chunks) {
                HttpStatusResponse response = sendData(chunk, endpointUrl);
                
                if (!response.success) {
                    // Parse the chunk back into messages for the error response
                    JSONArray chunkArray = new JSONArray(chunk);
                    List<JSONObject> chunkMessages = new ArrayList<>();
                    for (int i = 0; i < chunkArray.length(); i++) {
                        chunkMessages.add(chunkArray.getJSONObject(i));
                    }
                    throw new MixpanelServerException("Server refused to accept chunked messages, they may be malformed. HTTP " + response.statusCode, chunkMessages);
                }
            }
        } catch (JSONException e) {
            throw new MixpanelServerException("Failed to chunk messages due to JSON error", batch);
        } catch (UnsupportedEncodingException e) {
            throw new MixpanelServerException("Failed to chunk messages due to encoding error", batch);
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

        // Send messages in batches (configurable batch size for /import, default max 2000 per batch)
        // If token is empty, the server will reject with 401 Unauthorized
        for (int i = 0; i < messages.size(); i += mImportMaxMessageSize) {
            int endIndex = i + mImportMaxMessageSize;
            endIndex = Math.min(endIndex, messages.size());
            List<JSONObject> batch = messages.subList(i, endIndex);

            if (batch.size() > 0) {
                String messagesString = dataString(batch);
                boolean accepted = sendImportData(messagesString, endpointUrl, token);

                if (!accepted && mLastStatusCode == Config.HTTP_413_PAYLOAD_TOO_LARGE) {
                    // Retry with chunked payloads (only once) for 413 errors
                    sendImportMessagesChunked(batch, endpointUrl, token);
                } else if (!accepted) {
                    String respBody = mLastResponseBody != null ? mLastResponseBody : "no response body";
                    int status = mLastStatusCode;
                    throw new MixpanelServerException("Server refused to accept import messages, they may be malformed. HTTP " + status + " Response: " + respBody, batch);
                }
            }
        }
    }

    /**
     * Sends import messages by chunking the payload to handle 413 Payload Too Large errors.
     * This is a retry mechanism that only happens once when the initial send returns 413.
     * 
     * The messages are split into smaller chunks using PayloadChunker, with each chunk
     * sized to be under the import endpoint's limit (10 MB). Each chunk is sent independently,
     * and we do NOT retry chunked sends that fail - only the initial payload gets one retry.
     * 
     * @param batch the batch of messages to send in chunks
     * @param endpointUrl the endpoint URL
     * @param token the authentication token
     * @throws IOException if there's a network error
     * @throws MixpanelServerException if any chunk is rejected by the server
     */
    private void sendImportMessagesChunked(List<JSONObject> batch, String endpointUrl, String token) throws IOException {
        String originalPayload = dataString(batch);
        
        try {
            // Split the payload into chunks under the import endpoint's 10MB limit
            // Size limits are based on uncompressed data (server limits apply to uncompressed payloads)
            List<String> chunks = PayloadChunker.chunkJsonArray(originalPayload, Config.IMPORT_MAX_PAYLOAD_BYTES);
            
            for (String chunk : chunks) {
                boolean accepted = sendImportData(chunk, endpointUrl, token);
                
                if (!accepted) {
                    // Parse the chunk back into messages for the error response
                    JSONArray chunkArray = new JSONArray(chunk);
                    List<JSONObject> chunkMessages = new ArrayList<>();
                    for (int i = 0; i < chunkArray.length(); i++) {
                        chunkMessages.add(chunkArray.getJSONObject(i));
                    }
                    String respBody = mLastResponseBody != null ? mLastResponseBody : "no response body";
                    int status = mLastStatusCode;
                    throw new MixpanelServerException("Server refused to accept chunked import messages, they may be malformed. HTTP " + status + " Response: " + respBody, chunkMessages);
                }
            }
        } catch (JSONException e) {
            throw new MixpanelServerException("Failed to chunk import messages due to JSON error", batch);
        } catch (UnsupportedEncodingException e) {
            throw new MixpanelServerException("Failed to chunk import messages due to encoding error", batch);
        }
    }

    private String dataString(List<JSONObject> messages) {
        JSONArray array = new JSONArray();
        for (JSONObject message:messages) {
            array.put(message);
        }

        return array.toString();
    }

    /**
     * Sends import data to the /import endpoint with Basic Auth using the project token.
     * The /import endpoint requires:
     * - JSON content type (not URL-encoded like /track)
     * - Basic authentication with token as username and empty password
     * - strict=1 parameter for validation
     * 
     * When a 413 Payload Too Large error is received, the caller should split the payload
     * into smaller chunks using PayloadChunker and retry each chunk individually.
     * This method will store the 413 status code in mLastStatusCode for the caller to detect.
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
        conn.setReadTimeout(mReadTimeoutMillis);
        conn.setConnectTimeout(mConnectTimeoutMillis);
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
            // HTTP error codes (401, 400, 413, etc.) throw IOException when calling getInputStream()
            // Check if it's an HTTP error and read the error stream for details
            int statusCode = conn.getResponseCode();
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                try {
                    String errorResponse = slurp(errorStream);
                    mLastResponseBody = errorResponse;
                    mLastStatusCode = statusCode;
                    errorStream.close();
                    // Return false to indicate rejection, which will allow caller to check status code
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
            mLastResponseBody = null;
            mLastStatusCode = 0;
            return false;
        }

        // Parse JSON response
        try {
            JSONObject jsonResponse = new JSONObject(response);

            // Check for {"status":"OK"} and {"code":200}
            boolean statusOk = jsonResponse.has("status") && "OK".equals(jsonResponse.getString("status"));
            boolean codeOk = jsonResponse.has("code") && jsonResponse.getInt("code") == 200;

            if (statusOk && codeOk) {
                mLastResponseBody = response;
                mLastStatusCode = 200;
                return true;
            } else {
                mLastResponseBody = response;
                mLastStatusCode = jsonResponse.has("code") ? jsonResponse.getInt("code") : 0;
                return false;
            }
        } catch (JSONException e) {
            // Not valid JSON or missing expected fields
            mLastResponseBody = response;
            mLastStatusCode = 0;
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
     * Sets the connection timeout for HTTP requests.
     * 
     * Default is 2000 milliseconds (2 seconds). You may need to increase this for high-latency regions.
     * This should be called before calling any deliver() or sendMessage().
     * 
     * Example:
     *     MixpanelAPI api = new MixpanelAPI();
     *     api.setConnectTimeout(5000);  // 5 seconds for slow regions
     *     api.deliver(delivery);
     *
     * @param timeoutMillis timeout in milliseconds (must be > 0)
     * @throws IllegalArgumentException if timeoutMillis <= 0
     */
    public void setConnectTimeout(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Connect timeout must be > 0");
        }
        this.mConnectTimeoutMillis = timeoutMillis;
    }

    /**
     * Sets the read timeout for HTTP requests.
     * 
     * Default is 10000 milliseconds (10 seconds). You may need to increase this for high-latency regions
     * or when processing large batches of events.
     * This should be called before calling any deliver() or sendMessage().
     * 
     * Example:
     *     MixpanelAPI api = new MixpanelAPI();
     *     api.setReadTimeout(15000);  // 15 seconds for slow regions
     *     api.deliver(delivery);
     *
     * @param timeoutMillis timeout in milliseconds (must be > 0)
     * @throws IllegalArgumentException if timeoutMillis <= 0
     */
    public void setReadTimeout(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Read timeout must be > 0");
        }
        this.mReadTimeoutMillis = timeoutMillis;
    }

    /**
     * Disables strict validation for import operations.
     * 
     * By default, the /import endpoint uses strict=1 which validates each event and returns
     * a 400 error if any event has issues. Correctly formed events are still ingested, and
     * problematic events are returned in the response with error messages.
     * 
     * Calling this method sets strict=0, which bypasses validation and imports all events
     * regardless of their validity. This can be useful for importing data with known issues
     * or when validation errors are not a concern.
     * 
     * This should be called before calling any deliver() or sendMessage() methods.
     * 
     * Example:
     *     MixpanelAPI api = new MixpanelAPI();
     *     api.disableStrictImport();  // Skip validation on import
     *     api.deliver(delivery);
     *
     * For more details on import validation, see:
     *     https://developer.mixpanel.com/reference/import-events
     */
    public void disableStrictImport() {
        this.mStrictImportMode = false;
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

}
