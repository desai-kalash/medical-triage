package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triage.messages.Messages.*;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.*;

import java.io.IOException;

/**
 * LLMActor - AI Analysis Component with Correct Gemini API Integration
 * Demonstrates asynchronous processing and proper response handling for ASK pattern
 * Responsibility: Analyze symptoms using Gemini AI and return structured assessment
 */
public class LLMActor extends AbstractBehavior<LLMCommand> {

    private final ActorRef<LogCommand> logger;
    private final OkHttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final String apiKey;

    public static Behavior<LLMCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new LLMActor(context, logger));
    }

    private LLMActor(ActorContext<LLMCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
        this.httpClient = new OkHttpClient();
        this.jsonMapper = new ObjectMapper();
        
        Dotenv dotenv = Dotenv.load();
        this.apiKey = dotenv.get("GEMINI_API_KEY");
        
        getContext().getLog().info("🤖 LLMActor initialized with API key: {}", 
            apiKey != null && !apiKey.isEmpty() ? "✅ Present" : "❌ Missing");
    }

    @Override
    public Receive<LLMCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(AnalyzeSymptoms.class, this::onAnalyzeSymptoms)
                .build();
    }

    private Behavior<LLMCommand> onAnalyzeSymptoms(AnalyzeSymptoms msg) {
        getContext().getLog().info("🤖 Analyzing symptoms for session [{}]: {}", 
            msg.sessionId, msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
            "Starting AI analysis", "INFO"));

        if (apiKey == null || apiKey.isEmpty()) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "API key missing", "ERROR"));
            msg.replyTo.tell(LLMAnalysisResult.failure(msg.sessionId, "API key not configured"));
            return this;
        }

        // Build enhanced medical prompt
        String medicalPrompt = buildMedicalPrompt(msg.symptoms, msg.context);
        
        try {
            // Build CORRECT Gemini Pro API request payload
            ObjectNode requestPayload = jsonMapper.createObjectNode();
            
            // Create the contents array
            ObjectNode content = jsonMapper.createObjectNode();
            ObjectNode textPart = jsonMapper.createObjectNode();
            textPart.put("text", medicalPrompt);
            
            content.putArray("parts").add(textPart);
            requestPayload.putArray("contents").add(content);

            // Use CORRECT current Gemini API endpoint with proper model name
            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestPayload.toString(), 
                    MediaType.parse("application/json")))
                .build();

            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Sending request to Gemini 2.5 Flash API", "DEBUG"));

            // Asynchronous API call
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Don't use getContext() here - we're in OkHttp thread
                    logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                        "API call failed: " + e.getMessage(), "ERROR"));
                    
                    // Use fallback instead of network error
                    LLMAnalysisResult fallbackResult = generateFallbackAnalysis(msg.sessionId, msg.symptoms);
                    msg.replyTo.tell(fallbackResult);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            JsonNode json = jsonMapper.readTree(responseBody);
                            
                            // Parse Gemini response format
                            JsonNode candidates = json.get("candidates");
                            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                                JsonNode firstCandidate = candidates.get(0);
                                JsonNode content = firstCandidate.get("content");
                                
                                if (content != null && content.has("parts")) {
                                    JsonNode parts = content.get("parts");
                                    if (parts.isArray() && parts.size() > 0) {
                                        String aiResponse = parts.get(0).get("text").asText();
                                        
                                        // Don't use getContext() here - we're in OkHttp thread
                                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                            "AI analysis successful", "INFO"));

                                        // Parse AI response into structured result
                                        LLMAnalysisResult result = parseAIResponse(msg.sessionId, aiResponse);
                                        msg.replyTo.tell(result);
                                        return;
                                    }
                                }
                            }
                            
                            // If we get here, the response format was unexpected
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Unexpected API response format", "WARNING"));
                            
                            // Use fallback instead of failure
                            LLMAnalysisResult fallbackResult = generateFallbackAnalysis(msg.sessionId, msg.symptoms);
                            msg.replyTo.tell(fallbackResult);
                            
                        } catch (Exception ex) {
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Response parsing failed: " + ex.getMessage(), "ERROR"));
                            
                            // Use fallback instead of failure
                            LLMAnalysisResult fallbackResult = generateFallbackAnalysis(msg.sessionId, msg.symptoms);
                            msg.replyTo.tell(fallbackResult);
                        }
                    } else {
                        String errorMsg = "HTTP " + response.code();
                        if (response.body() != null) {
                            String errorBody = response.body().string();
                            errorMsg += ": " + errorBody;
                        }
                        
                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                            "API error: " + errorMsg + " - Using fallback analysis", "WARNING"));
                        
                        // Use fallback instead of failure
                        LLMAnalysisResult fallbackResult = generateFallbackAnalysis(msg.sessionId, msg.symptoms);
                        msg.replyTo.tell(fallbackResult);
                    }
                }
            });

        } catch (Exception e) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Request building failed: " + e.getMessage(), "ERROR"));
            msg.replyTo.tell(LLMAnalysisResult.failure(msg.sessionId, 
                "Request error: " + e.getMessage()));
        }

        return this;
    }

    private String buildMedicalPrompt(String symptoms, String context) {
        return String.format("""
            You are a medical triage assistant. First, determine if the input describes medical symptoms or health concerns.
            
            INPUT: %s
            
            MEDICAL CONTEXT: %s
            
            If this is NOT a medical query (like asking about capitals, general questions, etc.), respond with:
            NON_MEDICAL_INPUT
            
            If this IS a medical query, provide your assessment in this exact format:
            ANALYSIS: [Brief medical assessment]
            SEVERITY: [HIGH/MODERATE/LOW]
            RECOMMENDATION: [Specific action recommendation]
            
            Focus on safety and appropriate level of care needed.
            """, symptoms, context != null ? context : "General medical knowledge");
    }

    private LLMAnalysisResult parseAIResponse(String sessionId, String aiResponse) {
        try {
            // Check if this is a non-medical input
            if (aiResponse.trim().equals("NON_MEDICAL_INPUT") || 
                aiResponse.toUpperCase().contains("NON_MEDICAL_INPUT")) {
                return LLMAnalysisResult.success(sessionId, 
                    "Non-medical input detected", 
                    "NON_MEDICAL", 
                    "Please describe your medical symptoms or health concerns for triage assistance.");
            }
            
            String analysis = "";
            String severity = "MODERATE";
            String recommendation = "";

            // Parse structured response
            String[] lines = aiResponse.split("\n");
            for (String line : lines) {
                String upperLine = line.toUpperCase().trim();
                if (upperLine.startsWith("ANALYSIS:")) {
                    analysis = line.substring(line.indexOf(":") + 1).trim();
                } else if (upperLine.startsWith("SEVERITY:")) {
                    severity = line.substring(line.indexOf(":") + 1).trim();
                } else if (upperLine.startsWith("RECOMMENDATION:")) {
                    recommendation = line.substring(line.indexOf(":") + 1).trim();
                }
            }

            // Fallback if structured parsing fails
            if (analysis.isEmpty()) {
                analysis = aiResponse.length() > 200 ? 
                    aiResponse.substring(0, 200) + "..." : aiResponse;
            }
            if (recommendation.isEmpty()) {
                recommendation = "Consult with healthcare professional for proper assessment";
            }

            return LLMAnalysisResult.success(sessionId, analysis, severity, recommendation);
            
        } catch (Exception e) {
            return LLMAnalysisResult.success(sessionId, aiResponse, "MODERATE", 
                "Please consult with a healthcare professional");
        }
    }

    /**
     * Fallback analysis when Gemini API is unavailable
     * Uses rule-based medical triage logic
     */
    private LLMAnalysisResult generateFallbackAnalysis(String sessionId, String symptoms) {
        String symptomsLower = symptoms.toLowerCase();
        
        // Check for obvious non-medical inputs
        if (symptomsLower.contains("capital") || symptomsLower.contains("weather") ||
            symptomsLower.contains("hello") || symptomsLower.contains("hi") ||
            symptomsLower.contains("what is") || symptomsLower.contains("how to") ||
            symptomsLower.contains("calculate") || symptomsLower.contains("recipe") ||
            symptomsLower.contains("movie") || symptomsLower.contains("book")) {
            
            return LLMAnalysisResult.success(sessionId, 
                "Non-medical input detected", 
                "NON_MEDICAL", 
                "I'm a medical triage assistant. Please describe your medical symptoms or health concerns so I can help assess your situation.");
        }
        
        String analysis;
        String severity;
        String recommendation;
        
        // Emergency symptoms detection
        if (symptomsLower.contains("chest pain") || symptomsLower.contains("shortness of breath") ||
            symptomsLower.contains("difficulty breathing") || symptomsLower.contains("severe pain") ||
            symptomsLower.contains("unconscious") || symptomsLower.contains("bleeding")) {
            
            analysis = "Symptoms indicate potential medical emergency. " +
                      "Chest pain and breathing difficulties require immediate evaluation " +
                      "to rule out cardiac or pulmonary emergencies.";
            severity = "HIGH";
            recommendation = "Seek immediate emergency medical care. Call 911 or go to nearest ER.";
            
        } else if (symptomsLower.contains("mild") || symptomsLower.contains("minor") ||
                   symptomsLower.contains("headache") || symptomsLower.contains("runny nose") ||
                   symptomsLower.contains("sore throat") || symptomsLower.contains("cough")) {
            
            analysis = "Symptoms suggest minor condition likely manageable with home care. " +
                      "Appears to be common cold, minor headache, or similar mild condition.";
            severity = "LOW";
            recommendation = "Try home remedies, rest, hydration. Monitor for worsening.";
            
        } else {
            analysis = "Symptoms require medical evaluation to determine appropriate treatment. " +
                      "Not immediately life-threatening but warrant professional assessment.";
            severity = "MODERATE";
            recommendation = "Schedule appointment with healthcare provider within 1-2 weeks.";
        }
        
        return LLMAnalysisResult.success(sessionId, 
            analysis + " (Fallback analysis used)", severity, recommendation);
    }
}