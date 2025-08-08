package com.triage.retrieval;

/**
 * EmbeddingsClient - Interface for text embedding providers
 * Allows switching between Simple, Google, OpenAI embeddings
 */
public interface EmbeddingsClient {
    
    /**
     * Generate vector embedding for given text
     * @param text Input text to embed
     * @return Float array representing the text as a vector
     * @throws Exception if embedding fails
     */
    float[] embed(String text) throws Exception;
    
    /**
     * @return Number of dimensions in the embedding vector
     */
    int dimensions();
    
    /**
     * @return Name/identifier of this embedding provider
     */
    String name();
}