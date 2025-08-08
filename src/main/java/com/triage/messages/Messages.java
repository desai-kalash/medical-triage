package com.triage.messages;

import akka.actor.typed.ActorRef;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

/**
 * Centralized message definitions for the Medical Triage System
 * Demonstrates all Akka communication patterns: tell, ask, forward
 * Enhanced with Vector Database support for medical knowledge retrieval
 */
public class Messages {

    // ========== USER INPUT MESSAGES ==========
    public interface UserInputCommand {}
    
    public static class UserSymptomInput implements UserInputCommand {
        public final String sessionId;
        public final String symptoms;
        public final Instant timestamp;
        
        public UserSymptomInput(String symptoms) {
            this.sessionId = UUID.randomUUID().toString().substring(0, 8);
            this.symptoms = symptoms;
            this.timestamp = Instant.now();
        }
        
        public UserSymptomInput(String sessionId, String symptoms) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.timestamp = Instant.now();
        }
    }

    // ========== TRIAGE ROUTER MESSAGES ==========
    public interface TriageCommand {}
    
    public static class ProcessSymptoms implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final Instant timestamp;
        
        public ProcessSymptoms(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.timestamp = Instant.now();
        }
    }
    
    public static class TriageResponse {
        public final String sessionId;
        public final String originalSymptoms;
        public final String classification;
        public final String recommendation;
        public final String severity;
        public final boolean success;
        public final Instant timestamp;
        
        public TriageResponse(String sessionId, String originalSymptoms, String classification, 
                            String recommendation, String severity, boolean success) {
            this.sessionId = sessionId;
            this.originalSymptoms = originalSymptoms;
            this.classification = classification;
            this.recommendation = recommendation;
            this.severity = severity;
            this.success = success;
            this.timestamp = Instant.now();
        }
    }

    // ========== LLM MESSAGES ==========
    public interface LLMCommand {}
    
    public static class AnalyzeSymptoms implements LLMCommand {
        public final String sessionId;
        public final String symptoms;
        public final String context;
        public final ActorRef<LLMAnalysisResult> replyTo;
        
        public AnalyzeSymptoms(String sessionId, String symptoms, String context, 
                              ActorRef<LLMAnalysisResult> replyTo) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.context = context;
            this.replyTo = replyTo;
        }
    }
    
    public static class LLMAnalysisResult {
        public final String sessionId;
        public final String analysis;
        public final String severity;
        public final String recommendation;
        public final boolean success;
        public final String errorMessage;
        
        public LLMAnalysisResult(String sessionId, String analysis, String severity, 
                               String recommendation, boolean success, String errorMessage) {
            this.sessionId = sessionId;
            this.analysis = analysis;
            this.severity = severity;
            this.recommendation = recommendation;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public static LLMAnalysisResult success(String sessionId, String analysis, 
                                              String severity, String recommendation) {
            return new LLMAnalysisResult(sessionId, analysis, severity, recommendation, true, null);
        }
        
        public static LLMAnalysisResult failure(String sessionId, String errorMessage) {
            return new LLMAnalysisResult(sessionId, "", "", "", false, errorMessage);
        }
    }

    // ========== ENHANCED RETRIEVAL MESSAGES (VECTOR DATABASE) ==========
    public interface RetrievalCommand {}
    
    public static class Retrieve implements RetrievalCommand {
        public final String sessionId;
        public final String query;
        public final int topK;
        public final ActorRef<Retrieved> replyTo;
        
        public Retrieve(String sessionId, String query, int topK, ActorRef<Retrieved> replyTo) {
            this.sessionId = sessionId;
            this.query = query;
            this.topK = topK;
            this.replyTo = replyTo;
        }
    }
    
    public static class Retrieved {
        public final String sessionId;
        public final List<RetrievedChunk> chunks;
        public final boolean success;
        
        public Retrieved(String sessionId, List<RetrievedChunk> chunks, boolean success) {
            this.sessionId = sessionId;
            this.chunks = chunks;
            this.success = success;
        }
    }
    
    public static class RetrievedChunk {
        public final String id;
        public final String text;
        public final String sourceName;
        public final String sourceUrl;
        public final String category;
        public final double score;
        
        public RetrievedChunk(String id, String text, String sourceName, String sourceUrl, 
                            String category, double score) {
            this.id = id;
            this.text = text;
            this.sourceName = sourceName;
            this.sourceUrl = sourceUrl;
            this.category = category;
            this.score = score;
        }
    }

    // ========== CARE ACTOR MESSAGES ==========
    public interface CareCommand {}
    
    public static class HandleTriageCase implements CareCommand {
        public final String sessionId;
        public final String symptoms;
        public final String analysis;
        public final String severity;
        public final ActorRef<TriageResponse> originalSender;
        
        public HandleTriageCase(String sessionId, String symptoms, String analysis, 
                              String severity, ActorRef<TriageResponse> originalSender) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.analysis = analysis;
            this.severity = severity;
            this.originalSender = originalSender;
        }
    }

    // ========== LOGGING MESSAGES ==========
    public interface LogCommand {}
    
    public static class LogEvent implements LogCommand {
        public final String sessionId;
        public final String actorName;
        public final String event;
        public final String level;
        public final Instant timestamp;
        
        public LogEvent(String sessionId, String actorName, String event, String level) {
            this.sessionId = sessionId;
            this.actorName = actorName;
            this.event = event;
            this.level = level;
            this.timestamp = Instant.now();
        }
        
        public LogEvent(String sessionId, String actorName, String event) {
            this(sessionId, actorName, event, "INFO");
        }
    }

    // ========== SESSION MESSAGES ==========
    public interface SessionCommand {}
    
    public static class UpdateSession implements SessionCommand {
        public final String sessionId;
        public final String userInput;
        public final String systemResponse;
        public final Instant timestamp;
        
        public UpdateSession(String sessionId, String userInput, String systemResponse) {
            this.sessionId = sessionId;
            this.userInput = userInput;
            this.systemResponse = systemResponse;
            this.timestamp = Instant.now();
        }
    }
    
    public static class GetSessionHistory implements SessionCommand {
        public final String sessionId;
        public final ActorRef<SessionHistory> replyTo;
        
        public GetSessionHistory(String sessionId, ActorRef<SessionHistory> replyTo) {
            this.sessionId = sessionId;
            this.replyTo = replyTo;
        }
    }
    
    public static class SessionHistory {
        public final String sessionId;
        public final List<String> interactions;
        public final int totalInteractions;
        
        public SessionHistory(String sessionId, List<String> interactions) {
            this.sessionId = sessionId;
            this.interactions = interactions;
            this.totalInteractions = interactions.size();
        }
    }
}