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

    // ========== UI WEB INTERFACE MESSAGES ==========
    public interface UICommand {}
    
    public static class UIQuery implements UICommand {
        public final String text;
        public final String sessionId;
        public final ActorRef<UIResponse> replyTo;
        
        public UIQuery(String text, String sessionId, ActorRef<UIResponse> replyTo) {
            this.text = text;
            this.sessionId = sessionId != null && !sessionId.isEmpty() ? sessionId : 
                           UUID.randomUUID().toString().substring(0, 8);
            this.replyTo = replyTo;
        }
    }
    
    public static class UIResponse {
        public final String sessionId;
        public final String reply;              // Final text to show to user
        public final String route;              // Emergency/SelfCare/Appointment/NonMedical
        public final boolean emergency;         // True if requires immediate care
        public final List<Source> sources;     // Medical sources with attribution
        public final String disclaimer;         // Safety disclaimer
        
        public UIResponse(String sessionId, String reply, String route, boolean emergency,
                         List<Source> sources, String disclaimer) {
            this.sessionId = sessionId;
            this.reply = reply;
            this.route = route;
            this.emergency = emergency;
            this.sources = sources;
            this.disclaimer = disclaimer;
        }
        
        /**
         * Source - Medical source attribution
         */
        public static class Source {
            public final String name;
            public final String url;
            public final double score;
            
            public Source(String name, String url, double score) {
                this.name = name;
                this.url = url;
                this.score = score;
            }
        }
    }

    // ========== MEDICAL INTAKE MESSAGES (PHASE 1 ENHANCEMENT) ==========
    public interface IntakeCommand {}
    
    /**
     * Comprehensive medical intake data structure for enhanced patient assessment
     */
    public static class MedicalIntake {
        // Demographics
        public final int age;
        public final String gender;
        public final double weightKg;
        public final double heightCm;
        
        // Medical History
        public final List<String> currentMedications;
        public final List<String> allergies;
        public final List<String> medicalConditions;
        public final List<String> previousSurgeries;
        
        // Current Health Context
        public final int painScale;  // 0-10
        public final boolean hasFever;
        public final String activityLevel;  // "normal", "limited", "bedbound"
        public final String smokingStatus;  // "never", "former", "current"
        public final String alcoholUse;     // "none", "occasional", "regular", "heavy"
        
        // Symptom Context
        public final String symptomOnset;   // "sudden", "gradual", "chronic"
        public final String symptomDuration; // "minutes", "hours", "days", "weeks", "months"
        public final List<String> triggers;  // What makes symptoms better/worse
        public final boolean previousEpisodes;
        
        // Social Context
        public final String recentTravel;
        public final boolean sickContacts;
        public final String occupationalExposure;
        public final Instant timestamp;
        
        public MedicalIntake(int age, String gender, double weightKg, double heightCm,
                           List<String> currentMedications, List<String> allergies,
                           List<String> medicalConditions, List<String> previousSurgeries,
                           int painScale, boolean hasFever, String activityLevel,
                           String smokingStatus, String alcoholUse, String symptomOnset,
                           String symptomDuration, List<String> triggers, boolean previousEpisodes,
                           String recentTravel, boolean sickContacts, String occupationalExposure) {
            this.age = age;
            this.gender = gender;
            this.weightKg = weightKg;
            this.heightCm = heightCm;
            this.currentMedications = currentMedications != null ? currentMedications : List.of();
            this.allergies = allergies != null ? allergies : List.of();
            this.medicalConditions = medicalConditions != null ? medicalConditions : List.of();
            this.previousSurgeries = previousSurgeries != null ? previousSurgeries : List.of();
            this.painScale = painScale;
            this.hasFever = hasFever;
            this.activityLevel = activityLevel;
            this.smokingStatus = smokingStatus;
            this.alcoholUse = alcoholUse;
            this.symptomOnset = symptomOnset;
            this.symptomDuration = symptomDuration;
            this.triggers = triggers != null ? triggers : List.of();
            this.previousEpisodes = previousEpisodes;
            this.recentTravel = recentTravel;
            this.sickContacts = sickContacts;
            this.occupationalExposure = occupationalExposure;
            this.timestamp = Instant.now();
        }
        
        public int calculateBaseRiskScore() {
            int riskScore = 0;
            if (age > 65) riskScore += 2;
            else if (age > 45) riskScore += 1;
            if (currentMedications.size() > 3) riskScore += 1;
            if (medicalConditions.contains("diabetes")) riskScore += 2;
            if (medicalConditions.contains("heart disease")) riskScore += 2;
            if (smokingStatus.equals("current")) riskScore += 1;
            if (painScale > 7) riskScore += 1;
            return riskScore;
        }
        
        public String getRiskCategory() {
            int score = calculateBaseRiskScore();
            if (score >= 5) return "HIGH";
            if (score >= 3) return "MODERATE";
            return "LOW";
        }
    }
    
    public static class ProcessSymptomsWithIntake implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final MedicalIntake intake;
        public final ActorRef<TriageResponse> replyTo;
        public final Instant timestamp;
        
        public ProcessSymptomsWithIntake(String sessionId, String symptoms, MedicalIntake intake, 
                                       ActorRef<TriageResponse> replyTo) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.intake = intake;
            this.replyTo = replyTo;
            this.timestamp = Instant.now();
        }
    }
}