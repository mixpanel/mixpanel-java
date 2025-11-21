package com.mixpanel.mixpanelapi.internal;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance benchmark for comparing JSON serialization implementations.
 * Run this class directly to see performance comparisons.
 */
public class SerializerBenchmark {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int[] MESSAGE_COUNTS = {1, 10, 50, 100, 500, 1000, 2000};

    public static void main(String[] args) {
        System.out.println("JSON Serializer Performance Benchmark");
        System.out.println("=====================================\n");

        // Check if Jackson is available
        boolean jacksonAvailable = false;
        try {
            Class.forName("com.fasterxml.jackson.core.JsonFactory");
            jacksonAvailable = true;
            System.out.println("✓ Jackson is available on classpath");
        } catch (ClassNotFoundException e) {
            System.out.println("✗ Jackson is NOT available on classpath");
            System.out.println("  Add jackson-databind dependency to enable high-performance serialization\n");
        }

        // Create serializers
        JsonSerializer orgJsonSerializer = new OrgJsonSerializer();
        JsonSerializer jacksonSerializer = null;
        if (jacksonAvailable) {
            try {
                jacksonSerializer = new JacksonSerializer();
            } catch (NoClassDefFoundError e) {
                System.out.println("Failed to initialize Jackson serializer");
                jacksonAvailable = false;
            }
        }

        System.out.println("\nRunning benchmarks...\n");

        // Run benchmarks for different message counts
        for (int messageCount : MESSAGE_COUNTS) {
            System.out.println("Testing with " + messageCount + " messages:");

            List<JSONObject> messages = createTestMessages(messageCount);

            // Warmup
            warmup(orgJsonSerializer, messages);
            if (jacksonAvailable) {
                warmup(jacksonSerializer, messages);
            }

            // Benchmark org.json
            long orgJsonTime = benchmark(orgJsonSerializer, messages);
            System.out.printf("  org.json:  %,d ms (%.2f ms/msg)\n",
                orgJsonTime, (double) orgJsonTime / messageCount);

            // Benchmark Jackson if available
            if (jacksonAvailable) {
                long jacksonTime = benchmark(jacksonSerializer, messages);
                System.out.printf("  Jackson:   %,d ms (%.2f ms/msg)\n",
                    jacksonTime, (double) jacksonTime / messageCount);

                // Calculate improvement
                double improvement = (double) orgJsonTime / jacksonTime;
                System.out.printf("  Speedup:   %.2fx faster\n", improvement);
            }

            System.out.println();
        }

        // Memory usage comparison for large batch
        System.out.println("Memory Usage Test (2000 messages):");
        List<JSONObject> largeMessages = createTestMessages(2000);

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        // Test org.json memory usage
        try {
            for (int i = 0; i < 100; i++) {
                orgJsonSerializer.serializeArray(largeMessages);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.gc();
        long afterOrgJson = runtime.totalMemory() - runtime.freeMemory();
        long orgJsonMemory = afterOrgJson - beforeMemory;
        System.out.printf("  org.json memory usage: %,d bytes\n", orgJsonMemory);

        if (jacksonAvailable) {
            System.gc();
            beforeMemory = runtime.totalMemory() - runtime.freeMemory();

            try {
                for (int i = 0; i < 100; i++) {
                    jacksonSerializer.serializeArray(largeMessages);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.gc();
            long afterJackson = runtime.totalMemory() - runtime.freeMemory();
            long jacksonMemory = afterJackson - beforeMemory;
            System.out.printf("  Jackson memory usage:  %,d bytes\n", jacksonMemory);
            System.out.printf("  Memory savings:        %,d bytes (%.1f%%)\n",
                orgJsonMemory - jacksonMemory,
                ((double)(orgJsonMemory - jacksonMemory) / orgJsonMemory) * 100);
        }

        System.out.println("\nBenchmark complete!");
        System.out.println("\nRecommendation:");
        if (jacksonAvailable) {
            System.out.println("✓ Jackson is providing significant performance improvements.");
            System.out.println("  The library will automatically use Jackson for JSON serialization.");
        } else {
            System.out.println("⚠ Consider adding Jackson dependency for better performance:");
            System.out.println("  <dependency>");
            System.out.println("    <groupId>com.fasterxml.jackson.core</groupId>");
            System.out.println("    <artifactId>jackson-databind</artifactId>");
            System.out.println("    <version>2.15.3</version>");
            System.out.println("  </dependency>");
        }
    }

    private static List<JSONObject> createTestMessages(int count) {
        List<JSONObject> messages = new ArrayList<>(count);
        long timestamp = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            JSONObject message = new JSONObject();
            message.put("event", "test_event_" + i);
            message.put("$insert_id", "id_" + timestamp + "_" + i);
            message.put("time", timestamp - (i * 1000));

            JSONObject properties = new JSONObject();
            properties.put("$token", "test_token_12345");
            properties.put("distinct_id", "user_" + (i % 100));
            properties.put("mp_lib", "java");
            properties.put("$lib_version", "1.6.0");
            properties.put("index", i);
            properties.put("batch_size", count);
            properties.put("test_string", "This is a test string with some content to make it more realistic");
            properties.put("test_number", Math.random() * 1000);
            properties.put("test_boolean", i % 2 == 0);

            // Add nested object
            JSONObject nested = new JSONObject();
            nested.put("nested_value", "value_" + i);
            nested.put("nested_number", i * 10);
            properties.put("nested_object", nested);

            // Add array
            JSONArray array = new JSONArray();
            for (int j = 0; j < 5; j++) {
                array.put("item_" + j);
            }
            properties.put("test_array", array);

            message.put("properties", properties);
            messages.add(message);
        }

        return messages;
    }

    private static void warmup(JsonSerializer serializer, List<JSONObject> messages) {
        try {
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                serializer.serializeArray(messages);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long benchmark(JsonSerializer serializer, List<JSONObject> messages) {
        long startTime = System.nanoTime();

        try {
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                serializer.serializeArray(messages);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        long endTime = System.nanoTime();
        return (endTime - startTime) / 1_000_000; // Convert to milliseconds
    }
}