package com.triage.retrieval;

/**
 * VectorMath - Utilities for vector similarity calculations
 * Used for semantic search in medical knowledge base
 */
public final class VectorMath {
    
    /**
     * Calculate cosine similarity between two vectors
     * Returns value between -1 and 1, where 1 is identical, 0 is orthogonal
     * 
     * @param a First vector
     * @param b Second vector  
     * @return Cosine similarity score
     */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        // Prevent division by zero
        double denominator = Math.sqrt(normA) * Math.sqrt(normB) + 1e-9;
        return dot / denominator;
    }
    
    /**
     * Calculate cosine similarity with query vector against multiple document vectors
     * Returns array of similarity scores in same order as documents
     * 
     * @param query Query vector
     * @param documents Array of document vectors
     * @return Array of cosine similarity scores
     */
    public static double[] batchCosine(float[] query, float[][] documents) {
        double[] scores = new double[documents.length];
        for (int i = 0; i < documents.length; i++) {
            scores[i] = cosine(query, documents[i]);
        }
        return scores;
    }
    
    /**
     * Normalize a vector to unit length (L2 normalization)
     * 
     * @param vector Input vector
     * @return Normalized vector
     */
    public static float[] normalize(float[] vector) {
        double norm = 0.0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm < 1e-9) {
            return vector.clone(); // Return copy if zero vector
        }
        
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
    
    // Private constructor to prevent instantiation
    private VectorMath() {}
}