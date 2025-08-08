package com.triage.retrieval;

import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/**
 * SimpleEmbeddingsClient - Java-only embedding implementation
 * Uses deterministic hashing to 384 dimensions
 * Not state-of-the-art but good enough for demo and completely offline
 */
public class SimpleEmbeddingsClient implements EmbeddingsClient {
    
    private static final int DIMS = 384;

    @Override 
    public float[] embed(String text) {
        float[] vector = new float[DIMS];
        
        // Tokenize and hash each word
        StringTokenizer tokenizer = new StringTokenizer(text.toLowerCase());
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            int hash = Math.abs(murmur32(token.getBytes(StandardCharsets.UTF_8)));
            int index = hash % DIMS;
            vector[index] += 1.0f;
        }
        
        // L2 normalize the vector
        double norm = 0.0;
        for (float x : vector) {
            norm += x * x;
        }
        norm = Math.sqrt(Math.max(norm, 1e-9));
        
        for (int i = 0; i < DIMS; i++) {
            vector[i] /= (float) norm;
        }
        
        return vector;
    }

    @Override 
    public int dimensions() { 
        return DIMS; 
    }
    
    @Override 
    public String name() { 
        return "SIMPLE"; 
    }

    /**
     * Simple MurmurHash implementation for consistent hashing
     */
    private static int murmur32(byte[] data) {
        int h = 0x9747b28c;
        int len = data.length;
        int i = 0;
        
        while (len >= 4) {
            int k = (data[i] & 0xff) | ((data[i+1] & 0xff) << 8) |
                    ((data[i+2] & 0xff) << 16) | ((data[i+3] & 0xff) << 24);
            k *= 0x5bd1e995; 
            k ^= k >>> 24; 
            k *= 0x5bd1e995;
            h *= 0x5bd1e995; 
            h ^= k;
            i += 4; 
            len -= 4;
        }
        
        switch (len) {
            case 3: h ^= (data[i+2] & 0xff) << 16;
            case 2: h ^= (data[i+1] & 0xff) << 8;
            case 1: h ^= (data[i] & 0xff); h *= 0x5bd1e995;
        }
        
        h ^= h >>> 13; 
        h *= 0x5bd1e995; 
        h ^= h >>> 15;
        return h;
    }
}