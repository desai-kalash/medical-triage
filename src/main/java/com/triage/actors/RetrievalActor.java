package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;
import com.triage.retrieval.*;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced RetrievalActor - Vector Database Medical Knowledge Retrieval
 * Uses Lucene for semantic similarity search of medical knowledge chunks
 * Demonstrates ASK pattern response handling with enriched medical context
 */
public class RetrievalActor extends AbstractBehavior<RetrievalCommand> {

    private final ActorRef<LogCommand> logger;
    private final RetrievalConfig config;
    private final EmbeddingsClient embedClient;
    private LuceneVectorStore vectorStore;
    private boolean indexReady = false;

    public static Behavior<RetrievalCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new RetrievalActor(context, logger));
    }

    private RetrievalActor(ActorContext<RetrievalCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
        this.config = new RetrievalConfig();
        
        getContext().getLog().info("🔍 RetrievalActor initializing with config: {}", config);
        
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
                .onSignal(PostStop.class, this::onPostStop)  // Add signal handler here
                .build();
    }

    private Behavior<RetrievalCommand> onRetrieve(Retrieve msg) {
        getContext().getLog().info("🔍 Vector search for session [{}]: {}", 
            msg.sessionId, msg.query);
        
        logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
            "Starting semantic similarity search", "INFO"));

        if (!indexReady || vectorStore == null) {
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Vector index not ready - using fallback", "WARNING"));
            
            // Return empty result if index not ready
            msg.replyTo.tell(new Retrieved(msg.sessionId, Collections.emptyList(), false));
            return this;
        }

        try {
            // Generate query embedding
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Generating query embedding", "DEBUG"));
            
            float[] queryVector = embedClient.embed(msg.query);
            
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Query embedded (" + queryVector.length + "D), searching corpus", "DEBUG"));
            
            // Search similar chunks using vector similarity
            List<LuceneVectorStore.RetrievedDoc> searchResults = 
                vectorStore.search(queryVector, msg.topK);
            
            // Convert to message format and apply similarity filtering
            List<RetrievedChunk> chunks = searchResults.stream()
                .filter(doc -> doc.score >= config.minSimilarity)
                .map(doc -> new RetrievedChunk(
                    doc.id, 
                    doc.text, 
                    doc.source_name, 
                    doc.source_url, 
                    doc.category, 
                    doc.score))
                .collect(Collectors.toList());
            
            // Ensure we return at least 1 chunk if available (even if below threshold)
            if (chunks.isEmpty() && !searchResults.isEmpty()) {
                LuceneVectorStore.RetrievedDoc bestMatch = searchResults.get(0);
                chunks.add(new RetrievedChunk(
                    bestMatch.id, 
                    bestMatch.text, 
                    bestMatch.source_name, 
                    bestMatch.source_url, 
                    bestMatch.category, 
                    bestMatch.score));
                
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "Low similarity scores - using best available match (score: " + 
                    String.format("%.3f", bestMatch.score) + ")", "WARNING"));
            }
            
            // Log retrieval results with detailed information
            if (!chunks.isEmpty()) {
                String chunkInfo = chunks.stream()
                    .map(c -> String.format("%s:%s(%.3f)", c.category, c.id, c.score))
                    .collect(Collectors.joining(", "));
                
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "Retrieved " + chunks.size() + " chunks: " + chunkInfo, "INFO"));
                
                // Log sources used for traceability
                String sources = chunks.stream()
                    .map(c -> c.sourceName)
                    .distinct()
                    .collect(Collectors.joining(", "));
                
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "Medical sources: " + sources, "DEBUG"));
            } else {
                logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                    "No relevant medical knowledge found above similarity threshold", "WARNING"));
            }
            
            msg.replyTo.tell(new Retrieved(msg.sessionId, chunks, true));
            
        } catch (Exception e) {
            getContext().getLog().error("❌ Vector search failed for session [{}]: {}", 
                msg.sessionId, e.getMessage());
            
            logger.tell(new LogEvent(msg.sessionId, "RetrievalActor", 
                "Vector search failed: " + e.getMessage(), "ERROR"));
            
            // Return failure result
            msg.replyTo.tell(new Retrieved(msg.sessionId, Collections.emptyList(), false));
        }

        return this;
    }

    /**
     * Handle actor shutdown - cleanup vector store resources
     */
    private Behavior<RetrievalCommand> onPostStop(PostStop signal) {
        if (vectorStore != null) {
            try {
                vectorStore.close();
                getContext().getLog().info("🔒 Vector store closed successfully");
            } catch (Exception e) {
                getContext().getLog().warn("⚠️ Error closing vector store: {}", e.getMessage());
            }
        }
        return this;
    }
}