package com.triage.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * ApiModels - Data Transfer Objects for HTTP API
 * Clean DTOs for JSON serialization between browser and server
 */
public final class ApiModels {

    /**
     * ChatRequest - Request from the browser
     */
    public static final class ChatRequest {
        public final String text;
        public final String sessionId;

        @JsonCreator
        public ChatRequest(
                @JsonProperty("text") String text,
                @JsonProperty("sessionId") String sessionId) {
            this.text = text;
            this.sessionId = sessionId;
        }
    }

    /**
     * SourceRef - Medical source used for the answer
     */
    public static final class SourceRef {
        public final String name;
        public final String url;
        public final double score; // similarity score from vector search

        @JsonCreator
        public SourceRef(
                @JsonProperty("name") String name,
                @JsonProperty("url") String url,
                @JsonProperty("score") double score) {
            this.name = name;
            this.url = url;
            this.score = score;
        }
    }

    /**
     * ChatResponse - Response to the browser
     */
    public static final class ChatResponse {
        public final String sessionId;
        public final String reply;              // Final response text
        public final String route;              // "Emergency" | "SelfCare" | "Appointment" | "NonMedical"
        public final boolean emergency;         // True if requires immediate care
        public final List<SourceRef> sources;  // Medical sources consulted
        public final String disclaimer;         // Safety disclaimer

        @JsonCreator
        public ChatResponse(
                @JsonProperty("sessionId") String sessionId,
                @JsonProperty("reply") String reply,
                @JsonProperty("route") String route,
                @JsonProperty("emergency") boolean emergency,
                @JsonProperty("sources") List<SourceRef> sources,
                @JsonProperty("disclaimer") String disclaimer) {
            this.sessionId = sessionId;
            this.reply = reply;
            this.route = route;
            this.emergency = emergency;
            this.sources = sources;
            this.disclaimer = disclaimer;
        }
    }
}