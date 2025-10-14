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
import java.util.zip.GZIPOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
public class MixpanelAPI {

    private static final int BUFFER_SIZE = 256; // Small, we expect small responses.

    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int READ_TIMEOUT_MILLIS = 10000;

    protected final String mEventsEndpoint;
    protected final String mPeopleEndpoint;
    protected final String mGroupsEndpoint;
    protected final String mImportEndpoint;
    protected final boolean mUseGzipCompression;

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
        this(Config.BASE_ENDPOINT + "/track", Config.BASE_ENDPOINT + "/engage", Config.BASE_ENDPOINT + "/groups", Config.BASE_ENDPOINT + "/import", useGzipCompression);
    }

    /**
     * Create a MixpaneAPI associated with custom URLS for events and people updates.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint) {
        this(eventsEndpoint, peopleEndpoint, Config.BASE_ENDPOINT + "/groups", Config.BASE_ENDPOINT + "/import", false);
    }

    /**
     * Create a MixpaneAPI associated with custom URLS for the Mixpanel service.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @param groupsEndpoint a URL that will accept Mixpanel groups messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint, String groupsEndpoint) {
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, Config.BASE_ENDPOINT + "/import", false);
    }

    /**
     * Create a MixpaneAPI associated with custom URLS for the Mixpanel service.
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
        this(eventsEndpoint, peopleEndpoint, groupsEndpoint, importEndpoint, false);
    }

    /**
     * Create a MixpaneAPI associated with custom URLS for the Mixpanel service.
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
        mEventsEndpoint = eventsEndpoint;
        mPeopleEndpoint = peopleEndpoint;
        mGroupsEndpoint = groupsEndpoint;
        mImportEndpoint = importEndpoint;
        mUseGzipCompression = useGzipCompression;
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
        conn.setReadTimeout(READ_TIMEOUT_MILLIS);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
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
                String messagesString = dataString(batch);
                boolean accepted = sendImportData(messagesString, endpointUrl, token);

                if (! accepted) {
                    throw new MixpanelServerException("Server refused to accept import messages, they may be malformed.", batch);
                }
            }
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
     * @param dataString JSON array of events to import
     * @param endpointUrl The import endpoint URL
     * @param token The project token for Basic Auth
     * @return true if the server accepted the data
     * @throws IOException if there's a network error
     */
    /* package */ boolean sendImportData(String dataString, String endpointUrl, String token) throws IOException {
        URL endpoint = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        conn.setReadTimeout(READ_TIMEOUT_MILLIS);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
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

}
