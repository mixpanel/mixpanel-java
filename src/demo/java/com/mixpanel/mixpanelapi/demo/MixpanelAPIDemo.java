package com.mixpanel.mixpanelapi.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONObject;

import com.mixpanel.mixpanelapi.ClientDelivery;
import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;

/**
 * This is a simple demonstration of how you might use the Mixpanel
 * Java API in your programs.
 *
 */
public class MixpanelAPIDemo {
    

    public static String PROJECT_TOKEN = "bf2a25faaefdeed4aecde6e177d111bf"; // "YOUR TOKEN";
    public static long MILLIS_TO_WAIT = 10 * 1000;

    private static class DeliveryThread extends Thread {
        public DeliveryThread(Queue<JSONObject> messages, boolean useGzipCompression) {
            mMixpanel = new MixpanelAPI(useGzipCompression);
            mMessageQueue = messages;
            mUseGzipCompression = useGzipCompression;
        }

        @Override
        public void run() {
            try {
                while(true) {
                    int messageCount = 0;
                    ClientDelivery delivery = new ClientDelivery();
                    JSONObject message = null;
                    do {
                        message = mMessageQueue.poll();
                        if (message != null) {
                            System.out.println("WILL SEND MESSAGE" + (mUseGzipCompression ? " (with gzip compression)" : "") + ":\n" + message.toString());

                            messageCount = messageCount + 1;
                            delivery.addMessage(message);
                        }

                    } while(message != null);

                    mMixpanel.deliver(delivery);

                    System.out.println("Sent " + messageCount + " messages" + (mUseGzipCompression ? " with gzip compression" : "") + ".");
                    Thread.sleep(MILLIS_TO_WAIT);
                }
            } catch (IOException e) {
                throw new RuntimeException("Can't communicate with Mixpanel.", e);
            } catch (InterruptedException e) {
                System.out.println("Message process interrupted.");
            }
        }

        private final MixpanelAPI mMixpanel;
        private final Queue<JSONObject> mMessageQueue;
        private final boolean mUseGzipCompression;
    }

    public static void printUsage() {
        System.out.println("USAGE: java com.mixpanel.mixpanelapi.demo.MixpanelAPIDemo distinct_id");
        System.out.println("");
        System.out.println("This is a simple program demonstrating Mixpanel's Java library.");
        System.out.println("It reads lines from standard input and sends them to Mixpanel as events.");
        System.out.println("");
        System.out.println("The demo also shows:");
        System.out.println("  - Setting user properties");
        System.out.println("  - Tracking charges");
        System.out.println("  - Importing historical events");
        System.out.println("  - Incrementing user properties");
        System.out.println("  - Using gzip compression");
    }

    /**
     * @param args
     */
    public static void main(String[] args)
        throws IOException, InterruptedException {
        Queue<JSONObject> messages = new ConcurrentLinkedQueue<JSONObject>();
        Queue<JSONObject> messagesWithGzip = new ConcurrentLinkedQueue<JSONObject>();
        
        // Create two delivery threads - one without gzip and one with gzip compression
        DeliveryThread worker = new DeliveryThread(messages, false);
        DeliveryThread workerWithGzip = new DeliveryThread(messagesWithGzip, true);
        
        MessageBuilder messageBuilder = new MessageBuilder(PROJECT_TOKEN);

        if (args.length != 1) {
            printUsage();
            System.exit(1);
        }

        worker.start();
        workerWithGzip.start();
        
        String distinctId = args[0];
        BufferedReader inputLines = new BufferedReader(new InputStreamReader(System.in));
        String line = inputLines.readLine();

        // Set the first name of the associated user (to distinct id)
        Map<String, String> namePropsMap = new HashMap<String, String>();
        namePropsMap.put("$first_name", distinctId);
        JSONObject nameProps = new JSONObject(namePropsMap);
        JSONObject nameMessage = messageBuilder.set(distinctId, nameProps);
        messages.add(nameMessage);

        // Charge the user $2.50 for using the program :)
        JSONObject transactionMessage = messageBuilder.trackCharge(distinctId, 2.50, null);
        messages.add(transactionMessage);

        // Import a historical event (30 days ago) with explicit time and $insert_id
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
        Map<String, Object> importPropsMap = new HashMap<String, Object>();
        importPropsMap.put("time", thirtyDaysAgo);
        importPropsMap.put("$insert_id", "demo-import-" + System.currentTimeMillis());
        importPropsMap.put("Event Type", "Historical");
        importPropsMap.put("Source", "Demo Import");
        JSONObject importProps = new JSONObject(importPropsMap);
        JSONObject importMessage = messageBuilder.importEvent(distinctId, "Program Started", importProps);
        messages.add(importMessage);

        // Import another event using defaults (time and $insert_id auto-generated)
        Map<String, String> simpleImportProps = new HashMap<String, String>();
        simpleImportProps.put("Source", "Demo Simple Import");
        JSONObject simpleImportMessage = messageBuilder.importEvent(distinctId, "Simple Import Event", new JSONObject(simpleImportProps));
        messages.add(simpleImportMessage);

        // Import event with no properties at all (time and $insert_id both auto-generated)
        JSONObject minimalImportMessage = messageBuilder.importEvent(distinctId, "Minimal Import Event", null);
        messages.add(minimalImportMessage);

        // Demonstrate gzip compression by sending some messages with compression enabled
        System.out.println("\n=== Demonstrating gzip compression ===");
        
        // Send a regular event with gzip compression
        Map<String, String> gzipEventProps = new HashMap<String, String>();
        gzipEventProps.put("Compression", "gzip");
        gzipEventProps.put("Demo", "true");
        JSONObject gzipEvent = messageBuilder.event(distinctId, "Gzip Compressed Event", new JSONObject(gzipEventProps));
        messagesWithGzip.add(gzipEvent);
        
        // Send an import event with gzip compression
        long historicalTime = System.currentTimeMillis() - (60L * 24L * 60L * 60L * 1000L);
        Map<String, Object> gzipImportProps = new HashMap<String, Object>();
        gzipImportProps.put("time", historicalTime);
        gzipImportProps.put("$insert_id", "gzip-import-" + System.currentTimeMillis());
        gzipImportProps.put("Compression", "gzip");
        gzipImportProps.put("Event Type", "Historical with Gzip");
        JSONObject gzipImportEvent = messageBuilder.importEvent(distinctId, "Gzip Compressed Import", new JSONObject(gzipImportProps));
        messagesWithGzip.add(gzipImportEvent);
        
        System.out.println("Added events to gzip compression queue\n");

        while((line != null) && (line.length() > 0)) {
            System.out.println("SENDING LINE: " + line);
            Map<String, String> propMap = new HashMap<String, String>();
            propMap.put("Line Typed", line.trim());
            JSONObject props = new JSONObject(propMap);
            JSONObject eventMessage = messageBuilder.event(distinctId, "Typed Line", props);
            messages.add(eventMessage);

            Map<String, Long> lineCounter = new HashMap<String, Long>();
            lineCounter.put("Lines Typed", 1L);
            JSONObject incrementMessage = messageBuilder.increment(distinctId, lineCounter);
            messages.add(incrementMessage);

            line = inputLines.readLine();
        }

        while(! messages.isEmpty() || ! messagesWithGzip.isEmpty()) {
            Thread.sleep(1000);
        }

        worker.interrupt();
        workerWithGzip.interrupt();
    }
}
