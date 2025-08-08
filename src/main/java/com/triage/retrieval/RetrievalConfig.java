package com.triage.retrieval;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * RetrievalConfig - Configuration for vector database retrieval
 */
public class RetrievalConfig {
    public final String provider;          // SIMPLE | GOOGLE | OPENAI
    public final int topK;
    public final String indexPath;
    public final boolean rebuildOnStart;
    public final double minSimilarity;

    public RetrievalConfig() {
        Dotenv d = Dotenv.configure().ignoreIfMissing().load();
        this.provider = d.get("EMBED_PROVIDER", "SIMPLE");
        this.topK = Integer.parseInt(d.get("TOP_K", "5"));
        this.indexPath = d.get("LUCENE_INDEX_PATH", "target/retrieval-index");
        this.rebuildOnStart = Boolean.parseBoolean(d.get("REBUILD_INDEX_ON_START", "false"));
        this.minSimilarity = Double.parseDouble(d.get("MIN_SIMILARITY", "0.20"));
    }
    
    @Override
    public String toString() {
        return String.format("RetrievalConfig{provider='%s', topK=%d, indexPath='%s', rebuild=%s, minSim=%.2f}",
            provider, topK, indexPath, rebuildOnStart, minSimilarity);
    }
}