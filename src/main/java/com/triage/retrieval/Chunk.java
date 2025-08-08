package com.triage.retrieval;

import java.util.List;

/**
 * Chunk - Represents a medical knowledge chunk for vector search
 * Simplified for Lucene vector database integration
 */
public class Chunk {
    public String id;
    public String text;
    public String source_name;
    public String source_url;
    public String category;      // red_flag | self_care | appointment
    public List<String> tags;    // e.g., ["chest pain","dizziness"]
    
    // Runtime fields populated during search:
    public double score;         // similarity score from Lucene
    
    // Default constructor
    public Chunk() {}
    
    public Chunk(String id, String text, String source_name, String source_url, 
                String category, List<String> tags) {
        this.id = id;
        this.text = text;
        this.source_name = source_name;
        this.source_url = source_url;
        this.category = category;
        this.tags = tags;
        this.score = 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("Chunk{id='%s', category='%s', source='%s', score=%.3f}", 
            id, category, source_name, score);
    }
}