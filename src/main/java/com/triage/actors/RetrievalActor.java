package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;
import com.triage.retrieval.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 2B: Enhanced RetrievalActor - Hybrid Medical Knowledge Retrieval
 * Combines local vector database with live medical data fetching
 * Provides comprehensive coverage for any medical symptom
 */
public class RetrievalActor extends AbstractBehavior<RetrievalCommand> {

    private final ActorRef<LogCommand> logger;
    private final RetrievalConfig config;
    private final EmbeddingsClient embedClient;
    private final MedicalInfoFetcher medicalFetcher;  // PHASE 2B: Live data fetcher
    private LuceneVectorStore vectorStore;
    private boolean indexReady = false;
    
    // PHASE 2B: Similarity threshold for live fetching
    private static final double LIVE_FETCH_THRESHOLD = 0.60;

    public static Behavior<RetrievalCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new RetrievalActor(context, logger));
    }

    private RetrievalActor(ActorContext<RetrievalCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
        this.config = new RetrievalConfig();
        this.medicalFetcher = new MedicalInfoFetcher();  // PHASE 2B: Initialize live fetcher
        
        getContext().getLog().info("🔍 RetrievalActor initializing with config: {}", config);
        getContext().getLog().info("🌐 PHASE 2B: Hybrid retrieval enabled (local + live medical data)");
        
        // Initialize embedding client based on config
        switch (config.provider.toUpperCase()) {
            case "SIMPLE":
            default:
                this.embedClient = new SimpleEmbeddingsClient();
                break;
            // Future: Add GoogleEmbeddingsClient, OpenAIEmbeddingsClient
        }
        
        getContext().getLog().info("🧠 Using embedding provider: {} ({} dimensions)", 
            embedClient.name(), embedClient.dimensions());
        
        // Initialize vector store
        try {
            initializeVectorStore();
            indexReady = true;
            getContext().getLog().info("✅ Vector database ready for semantic search");
        } catch (Exception e) {
            getContext().getLog().error("❌ Failed to initialize vector store: {}", e.getMessage());
            indexReady = false;
        }
    }

    private void initializeVectorStore() throws Exception {
        vectorStore = new LuceneVectorStore(config.indexPath, embedClient);
        
        // Check if we need to build index
        java.io.File indexDir = new java.io.File(config.indexPath);
        boolean needBuild = config.rebuildOnStart || !indexDir.exists() || 
                           (indexDir.list() != null && indexDir.list().length == 0);

        if (needBuild) {
            getContext().getLog().info("🔨 Building vector index from medical corpus...");
            
            logger.tell(new LogEvent("SYSTEM", "RetrievalActor", 
                "Building vector index with provider: " + embedClient.name(), "INFO"));
            
            // Load corpus from resources
            InputStream corpusStream = getClass().getResourceAsStream("/retrieval/corpus.jsonl");
            if (corpusStream == null) {
                throw new RuntimeException("❌ Corpus file not found: /retrieval/corpus.jsonl. " +
                    "Please ensure the medical knowledge corpus exists.");
            }
            
            vectorStore.buildIndexFromCorpus(corpusStream);
            corpusStream.close();
            
            getContext().getLog().info("✅ Vector index built successfully");
            logger.tell(new LogEvent("SYSTEM", "RetrievalActor", 
                "Vector index build completed", "INFO"));
        } else {
            getContext().getLog().info("📚 Using existing vector index");
        }
        
        vectorStore.openForSearch();
    }

    @Override
    public Receive<RetrievalCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(Retrieve.class, this::onRetrieve)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    /**
     * PHASE 2B: Enhanced retrieval with hybrid local + live fetching
     */
    private Behavior<RetrievalCommand> onRetrieve(Retrieve msg) {
        getContext().getLog().info("🔍 Vector search for session [{}]: {}", 
            msg.sessionId, msg.query);
        
        logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
            "Starting hybrid retrieval (local + live)", "INFO"));

        if (!indexReady || vectorStore == null) {
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Vector index not ready - using live fallback only", "WARNING"));
            
            // If local index not ready, try live fetching
            List<RetrievedChunk> liveFallback = fetchLiveMedicalData(msg.query, msg.sessionId);
            msg.replyTo.tell(new Retrieved(msg.sessionId, liveFallback, !liveFallback.isEmpty()));
            return this;
        }

        try {
            // PHASE 2B: STEP 1 - Search local corpus first (fast)
            List<RetrievedChunk> localResults = searchLocalCorpus(msg.query, msg.topK, msg.sessionId);
            
            // PHASE 2B: STEP 2 - Evaluate local results quality
            double bestLocalScore = localResults.isEmpty() ? 0.0 : 
                localResults.stream().mapToDouble(c -> c.score).max().orElse(0.0);
            
            List<RetrievedChunk> finalResults = new ArrayList<>();
            
            // PHASE 2B: STEP 3 - Decision: Use local or fetch live data
            if (bestLocalScore >= LIVE_FETCH_THRESHOLD) {
                // Good local match - use local results
                finalResults.addAll(localResults);
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "Using local corpus - good match found (best score: " + String.format("%.3f", bestLocalScore) + ")", "INFO"));
                
            } else {
                // Poor local match - fetch live medical data
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "Local match insufficient (best score: " + String.format("%.3f", bestLocalScore) + 
                    ") - fetching live medical data", "INFO"));
                
                List<RetrievedChunk> liveResults = fetchLiveMedicalData(msg.query, msg.sessionId);
                
                if (!liveResults.isEmpty()) {
                    // Use live results (higher quality and relevance)
                    finalResults.addAll(liveResults);
                    logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                        "Live medical data retrieved: " + liveResults.size() + " authoritative sources", "INFO"));
                    
                    // Log the improvement in relevance
                    double bestLiveScore = liveResults.stream().mapToDouble(c -> c.score).max().orElse(0.0);
                    logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                        "Relevance improved: " + String.format("%.3f", bestLocalScore) + " → " + 
                        String.format("%.3f", bestLiveScore), "INFO"));
                    
                } else {
                    // Live fetch failed - fallback to local results
                    finalResults.addAll(localResults);
                    logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                        "Live fetch failed - using local results as fallback", "WARNING"));
                }
            }
            
            // PHASE 2B: STEP 4 - Log comprehensive retrieval summary
            logRetrievalSummary(msg.sessionId, finalResults);
            
            // Return results to TriageRouterActor
            msg.replyTo.tell(new Retrieved(msg.sessionId, finalResults, true));
            
        } catch (Exception e) {
            getContext().getLog().error("❌ Hybrid retrieval failed for session [{}]: {}", 
                msg.sessionId, e.getMessage());
            
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Hybrid retrieval system error: " + e.getMessage(), "ERROR"));
            
            // Return failure result
            msg.replyTo.tell(new Retrieved(msg.sessionId, Collections.emptyList(), false));
        }

        return this;
    }
    
    /**
     * PHASE 2B: Search local vector corpus (existing logic enhanced)
     */
    private List<RetrievedChunk> searchLocalCorpus(String query, int topK, String sessionId) {
        try {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Searching local vector corpus", "DEBUG"));
            
            // Generate query embedding
            float[] queryVector = embedClient.embed(query);
            
            // Search similar chunks using vector similarity
            List<LuceneVectorStore.RetrievedDoc> searchResults = 
                vectorStore.search(queryVector, topK);
            
            // Convert to RetrievedChunk format
            List<RetrievedChunk> chunks = searchResults.stream()
                .map(doc -> new RetrievedChunk(
                    doc.id, 
                    doc.text, 
                    doc.source_name, 
                    doc.source_url, 
                    doc.category, 
                    doc.score))
                .collect(Collectors.toList());
            
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Local corpus search completed: " + chunks.size() + " chunks found", "DEBUG"));
            
            return chunks;
            
        } catch (Exception e) {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Local corpus search failed: " + e.getMessage(), "ERROR"));
            return Collections.emptyList();
        }
    }
    
    /**
     * PHASE 2B: Fetch live medical data from trusted sources
     */
    private List<RetrievedChunk> fetchLiveMedicalData(String query, String sessionId) {
        try {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Initiating live medical data fetch", "INFO"));
            
            // Extract primary symptom for targeted fetching
            String primarySymptom = medicalFetcher.extractPrimarySymptom(query);
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Primary symptom identified: " + primarySymptom, "DEBUG"));
            
            // Fetch from multiple trusted medical sources
            List<RetrievedChunk> liveChunks = medicalFetcher.fetchMedicalInfo(primarySymptom, sessionId);
            
            if (!liveChunks.isEmpty()) {
                // Log successful live retrieval
                String liveSourceInfo = liveChunks.stream()
                    .map(c -> c.sourceName + "(" + String.format("%.2f", c.score) + ")")
                    .collect(Collectors.joining(", "));
                
                logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                    "Live medical sources: " + liveSourceInfo, "INFO"));
                
                // Cache best results for future use (optional)
                cacheBestLiveResults(liveChunks, primarySymptom, sessionId);
            }
            
            return liveChunks;
            
        } catch (Exception e) {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Live medical data fetch failed: " + e.getMessage(), "ERROR"));
            return Collections.emptyList();
        }
    }
    
    /**
     * PHASE 2B: Cache high-quality live results for future use
     */
    private void cacheBestLiveResults(List<RetrievedChunk> liveChunks, String symptom, String sessionId) {
        try {
            // Find the highest quality live result
            Optional<RetrievedChunk> bestResult = liveChunks.stream()
                .filter(c -> c.score > 0.85)  // Only cache high-quality results
                .max(Comparator.comparingDouble(c -> c.score));
            
            if (bestResult.isPresent()) {
                RetrievedChunk chunk = bestResult.get();
                logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                    "Caching high-quality live result: " + chunk.sourceName + " (score: " + 
                    String.format("%.2f", chunk.score) + ")", "INFO"));
                
                // TODO: In production, append to corpus.jsonl for persistent caching
                // For now, just log the caching action
                System.out.println("💾 PHASE 2B: Would cache " + chunk.sourceName + 
                    " data for '" + symptom + "' (score: " + String.format("%.2f", chunk.score) + ")");
            }
        } catch (Exception e) {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Caching attempt failed: " + e.getMessage(), "WARNING"));
        }
    }
    
    /**
     * PHASE 2B: Comprehensive retrieval summary logging
     */
    private void logRetrievalSummary(String sessionId, List<RetrievedChunk> finalResults) {
        if (finalResults.isEmpty()) {
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "No medical knowledge retrieved from any source", "WARNING"));
            return;
        }
        
        // Separate local vs live results
        List<RetrievedChunk> localChunks = finalResults.stream()
            .filter(c -> !c.id.startsWith("live_"))
            .collect(Collectors.toList());
            
        List<RetrievedChunk> liveChunks = finalResults.stream()
            .filter(c -> c.id.startsWith("live_"))
            .collect(Collectors.toList());
        
        // Log detailed breakdown
        if (!localChunks.isEmpty()) {
            String localInfo = localChunks.stream()
                .map(c -> String.format("%s(%.3f)", c.id, c.score))
                .collect(Collectors.joining(", "));
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Local corpus results: " + localInfo, "INFO"));
        }
        
        if (!liveChunks.isEmpty()) {
            String liveInfo = liveChunks.stream()
                .map(c -> String.format("%s(%.3f)", c.sourceName, c.score))
                .collect(Collectors.joining(", "));
            logger.tell(new LogEvent(sessionId, "RetrievalActor", 
                "Live medical data: " + liveInfo, "INFO"));
        }
        
        // Overall summary
        String allSources = finalResults.stream()
            .map(c -> c.sourceName)
            .distinct()
            .collect(Collectors.joining(", "));
        
        double avgScore = finalResults.stream()
            .mapToDouble(c -> c.score)
            .average()
            .orElse(0.0);
            
        logger.tell(new LogEvent(sessionId, "RetrievalActor", 
            "Hybrid retrieval completed - Sources: " + allSources + 
            ", Avg relevance: " + String.format("%.3f", avgScore), "INFO"));
    }

    /**
     * Handle actor shutdown - cleanup resources
     */
    private Behavior<RetrievalCommand> onPostStop(PostStop signal) {
        // Cleanup vector store
        if (vectorStore != null) {
            try {
                vectorStore.close();
                getContext().getLog().info("🔒 Vector store closed successfully");
            } catch (Exception e) {
                getContext().getLog().warn("⚠️ Error closing vector store: {}", e.getMessage());
            }
        }
        
        // Cleanup live fetcher
        if (medicalFetcher != null) {
            try {
                medicalFetcher.close();
                getContext().getLog().info("🌐 Medical info fetcher closed successfully");
            } catch (Exception e) {
                getContext().getLog().warn("⚠️ Error closing medical fetcher: {}", e.getMessage());
            }
        }
        
        return this;
    }
}