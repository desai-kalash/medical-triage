package com.triage.retrieval;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LuceneVectorStore - Simple vector database using Apache Lucene
 * Maximum compatibility - works with any Lucene version
 */
public class LuceneVectorStore implements Closeable {
    
    private final File indexDir;
    private final EmbeddingsClient embedClient;
    private Directory directory;
    private IndexWriter writer;
    private DirectoryReader reader;
    private IndexSearcher searcher;
    
    // In-memory storage for vectors (simple approach)
    private final Map<String, float[]> vectorCache = new HashMap<>();

    public LuceneVectorStore(String indexPath, EmbeddingsClient embedClient) throws Exception {
        this.indexDir = new File(indexPath);
        this.embedClient = embedClient;
        this.directory = FSDirectory.open(indexDir.toPath());
    }

    /**
     * Build Lucene index from JSONL corpus file
     */
    public void buildIndexFromCorpus(InputStream corpusJsonl) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        this.writer = new IndexWriter(directory, config);

        ObjectMapper mapper = new ObjectMapper();
        vectorCache.clear(); // Clear any existing vectors
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(corpusJsonl, StandardCharsets.UTF_8))) {
            
            String line;
            int count = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                try {
                    JsonNode json = mapper.readTree(line);

                    String id = json.get("id").asText();
                    String text = json.get("text").asText();
                    String sourceName = json.has("source_name") ? json.get("source_name").asText() : "";
                    String sourceUrl = json.has("source_url") ? json.get("source_url").asText() : "";
                    String category = json.has("category") ? json.get("category").asText() : "";
                    
                    List<String> tags = new ArrayList<>();
                    if (json.has("tags") && json.get("tags").isArray()) {
                        json.get("tags").forEach(node -> tags.add(node.asText()));
                    }

                    // Generate embedding for this text
                    float[] vector = embedClient.embed(text);
                    
                    // Store vector in memory cache
                    vectorCache.put(id, vector);

                    // Create Lucene document (no vector storage in Lucene - we use memory)
                    Document doc = new Document();
                    doc.add(new StringField("id", id, Field.Store.YES));
                    doc.add(new StoredField("text", text));
                    doc.add(new StoredField("source_name", sourceName));
                    doc.add(new StoredField("source_url", sourceUrl));
                    doc.add(new StoredField("category", category));
                    doc.add(new StoredField("tags", String.join(",", tags)));
                    
                    writer.addDocument(doc);
                    count++;
                    
                } catch (Exception e) {
                    System.err.println("Failed to process line: " + line + " - " + e.getMessage());
                }
            }
            
            System.out.println("📚 Indexed " + count + " medical knowledge chunks");
            System.out.println("🧠 Cached " + vectorCache.size() + " vectors in memory");
        }
        
        writer.commit();
        writer.close();
        this.writer = null;
    }

    /**
     * Open index for searching
     */
    public void openForSearch() throws Exception {
        this.reader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(reader);
    }

    /**
     * Search for similar documents using in-memory vector similarity
     * Completely avoids Lucene vector compatibility issues
     */
    public List<RetrievedDoc> search(float[] queryVector, int topK) throws Exception {
        List<RetrievedDoc> allResults = new ArrayList<>();
        
        // Simple approach: get all documents, then filter and rank
        TopDocs allDocs = searcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
        
        for (ScoreDoc scoreDoc : allDocs.scoreDocs) {
            Document doc = searcher.doc(scoreDoc.doc);
            String id = doc.get("id");
            
            // Get vector from memory cache
            float[] docVector = vectorCache.get(id);
            if (docVector == null) continue;
            
            // Calculate cosine similarity
            double similarity = calculateCosineSimilarity(queryVector, docVector);
            
            RetrievedDoc result = new RetrievedDoc();
            result.id = id;
            result.text = doc.get("text");
            result.source_name = doc.get("source_name");
            result.source_url = doc.get("source_url");
            result.category = doc.get("category");
            
            String tagsString = doc.get("tags");
            result.tags = tagsString != null && !tagsString.isEmpty() ? 
                Arrays.asList(tagsString.split(",")) : new ArrayList<>();
                
            result.score = (float) similarity;
            
            allResults.add(result);
        }
        
        // Sort by similarity (highest first) and take top-K
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.score, a.score))
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private double calculateCosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        double denominator = Math.sqrt(normA) * Math.sqrt(normB) + 1e-9;
        return dot / denominator;
    }

    @Override 
    public void close() throws IOException {
        if (reader != null) reader.close();
        if (directory != null) directory.close();
    }

    /**
     * RetrievedDoc - Result from vector search
     */
    public static class RetrievedDoc {
        public String id;
        public String text;
        public String source_name;
        public String source_url;
        public String category;
        public List<String> tags;
        public float score;
        
        @Override
        public String toString() {
            return String.format("RetrievedDoc{id='%s', category='%s', score=%.3f}", 
                id, category, score);
        }
    }
}