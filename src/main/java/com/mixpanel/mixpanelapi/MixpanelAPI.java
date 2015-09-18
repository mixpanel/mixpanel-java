package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;

import org.json.JSONArray;
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

    /**
     * Constructs a MixpanelAPI object associated with the production, Mixpanel services.
     */
    public MixpanelAPI() {
        this(Config.BASE_ENDPOINT + "/track", Config.BASE_ENDPOINT + "/engage");
    }

    /**
     * Create a MixpaneAPI associated with custom URLS for the Mixpanel service.
     *
     * Useful for testing and proxying. Most callers should use the constructor with no arguments.
     *
     * @param eventsEndpoint a URL that will accept Mixpanel events messages
     * @param peopleEndpoint a URL that will accept Mixpanel people messages
     * @see #MixpanelAPI()
     */
    public MixpanelAPI(String eventsEndpoint, String peopleEndpoint) {
        mEventsEndpoint = eventsEndpoint;
        mPeopleEndpoint = peopleEndpoint;
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
    }

    /**
     * Package scope for mocking purposes
     */
    /* package */ boolean sendData(String dataString, String endpointUrl) throws IOException {
        URL endpoint = new URL(endpointUrl);
        URLConnection conn = endpoint.openConnection();
        conn.setReadTimeout(READ_TIMEOUT_MILLIS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf8");

        byte[] utf8data;
        try {
            utf8data = dataString.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Mixpanel library requires utf-8 support", e);
        }

        String base64data = new String(Base64Coder.encode(utf8data));
        String encodedData = URLEncoder.encode(base64data, "utf8");
        String encodedQuery = "data=" + encodedData;

        OutputStream postStream = null;
        try {
            postStream = conn.getOutputStream();
            postStream.write(encodedQuery.getBytes());
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

    private String dataString(List<JSONObject> messages) {
        JSONArray array = new JSONArray();
        for (JSONObject message:messages) {
            array.put(message);
        }

        return array.toString();
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

    private final String mEventsEndpoint;
    private final String mPeopleEndpoint;

    private static final int BUFFER_SIZE = 256; // Small, we expect small responses.
    private static final int READ_TIMEOUT_MILLIS = 30000; // Thirty seconds should be more than enough for a response.

}
