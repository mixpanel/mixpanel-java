package com.mixpanel.mixpanelapi;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Utility class for safely chunking JSON array payloads while maintaining data integrity.
 * 
 * This class is used to split large payloads into smaller chunks when the server returns a 413
 * Payload Too Large error. It ensures that:
 * - The payload remains valid JSON (JSONArray format)
 * - Chunk size limits are based on UNCOMPRESSED data (server limits apply to uncompressed payloads)
 * - Each chunk is below the specified size limit
 * 
 * The chunking strategy removes individual messages from the original payload and creates new
 * JSONArray chunks, ensuring that the JSON structure remains valid and parseable.
 */
public class PayloadChunker {

    /**
     * Splits a JSON array string into multiple chunks, each under the specified byte size limit.
     * 
     * Size limits are calculated based on UNCOMPRESSED UTF-8 encoded data, since the server's
     * payload size limits (1 MB for /track, 10 MB for /import) apply to uncompressed payloads
     * before any gzip compression is applied.
     * 
     * This method is useful when a server returns a 413 Payload Too Large error. It splits the
     * original payload into smaller chunks such that each chunk's uncompressed size stays under
     * the specified limit.
     * 
     * Performance: O(n) time complexity by tracking cumulative size instead of re-serializing
     * the entire chunk after each item addition.
     * 
     * @param jsonArrayString the JSON array string to chunk (e.g., "[{...}, {...}, ...]")
     * @param maxBytesPerChunk the maximum size in bytes per chunk (uncompressed data)
     * @return a list of JSON array strings, each under the size limit
     * @throws JSONException if the input is not a valid JSON array
     * @throws UnsupportedEncodingException if UTF-8 encoding is not supported
     */
    public static List<String> chunkJsonArray(String jsonArrayString, int maxBytesPerChunk)
            throws JSONException, UnsupportedEncodingException {
        
        JSONArray originalArray = new JSONArray(jsonArrayString);
        List<String> chunks = new ArrayList<>();
        
        if (originalArray.length() == 0) {
            chunks.add("[]");
            return chunks;
        }
        
        // If the array has only one item and it's already too large, we still need to return it
        // (the server will reject it, but we can't split a single item)
        if (originalArray.length() == 1) {
            chunks.add(jsonArrayString);
            return chunks;
        }
        
        JSONArray currentChunk = new JSONArray();
        int currentChunkSize = 2; // Account for opening "[" and closing "]"
        
        for (int i = 0; i < originalArray.length(); i++) {
            Object item = originalArray.get(i);
            
            // Calculate the size of this item when serialized
            String itemString = item.toString();
            int itemSize = getUncompressedPayloadSize(itemString);
            
            // Account for comma separator if this isn't the first item in the chunk
            int itemWithSeparator = itemSize + (currentChunk.length() > 0 ? 1 : 0);
            
            // Check if adding this item would exceed the limit
            int sizeIfAdded = currentChunkSize + itemWithSeparator;
            
            if (sizeIfAdded > maxBytesPerChunk && currentChunk.length() > 0) {
                // Current item would push us over the limit, so save current chunk
                chunks.add(currentChunk.toString());
                
                // Start a new chunk with this item
                currentChunk = new JSONArray();
                currentChunk.put(item);
                currentChunkSize = 2 + itemSize; // "[" + item + "]"
            } else {
                // Item fits in current chunk
                currentChunk.put(item);
                currentChunkSize += itemWithSeparator;
            }
        }
        
        // Add the remaining items
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }
    
    /**
     * Calculates the uncompressed byte size of a string payload in UTF-8 encoding.
     * 
     * This method always returns the uncompressed size because the server's payload limits
     * (1 MB for /track, 10 MB for /import) apply to the uncompressed data before any
     * gzip compression is applied during transmission.
     * 
     * @param payload the string payload to measure
     * @return the size in bytes (uncompressed UTF-8 encoded)
     * @throws UnsupportedEncodingException if UTF-8 encoding is not supported
     */
    public static int getUncompressedPayloadSize(String payload)
            throws UnsupportedEncodingException {
        return payload.getBytes("UTF-8").length;
    }
}
