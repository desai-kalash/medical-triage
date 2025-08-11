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
 * PHASE A1: Enhanced LLMActor - Doctor-Level Medical Intelligence with Conversation Memory
 * Now provides structured clinical assessment with conversation awareness
 * Handles both initial consultations and follow-up conversations
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
        
        getContext().getLog().info("🩺 PHASE 1A: Enhanced medical reasoning enabled");
        getContext().getLog().info("💬 PHASE A1: Conversation awareness enabled");
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
        
        // PHASE A1: Check if this is a conversation follow-up
        boolean isConversationFollowUp = msg.context.contains("MEDICAL CONVERSATION HISTORY:");
        
        if (isConversationFollowUp) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Starting conversation-aware clinical analysis", "INFO"));
        } else {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Starting initial clinical analysis", "INFO"));
        }

        if (apiKey == null || apiKey.isEmpty()) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "API key missing - using conversation-aware medical fallback", "ERROR"));
            msg.replyTo.tell(generateConversationAwareFallback(msg.sessionId, msg.symptoms, msg.context));
            return this;
        }

        // PHASE A1: Build conversation-aware medical reasoning prompt
        String enhancedPrompt = buildConversationAwareMedicalPrompt(msg.symptoms, msg.context);
        
        // Log prompt type for debugging
        String promptType = msg.context.contains("MEDICAL CONVERSATION HISTORY:") ? 
            "conversation follow-up" : "initial consultation";
        getContext().getLog().debug("💬 PHASE A1: Building {} prompt", promptType);
        
        try {
            // Build enhanced Gemini request
            ObjectNode requestPayload = jsonMapper.createObjectNode();
            
            ObjectNode content = jsonMapper.createObjectNode();
            ObjectNode textPart = jsonMapper.createObjectNode();
            textPart.put("text", enhancedPrompt);
            
            content.putArray("parts").add(textPart);
            requestPayload.putArray("contents").add(content);
            
            // PHASE A1: Enhanced generation config for conversation-aware medical reasoning
            ObjectNode generationConfig = jsonMapper.createObjectNode();
            generationConfig.put("temperature", 0.2);      // Even lower for consistent conversation reasoning
            generationConfig.put("maxOutputTokens", 1200); // More tokens for conversation context
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 40);
            requestPayload.set("generationConfig", generationConfig);

            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestPayload.toString(), 
                    MediaType.parse("application/json")))
                .build();

            String logMessage = isConversationFollowUp ? 
                "Sending conversation-aware clinical reasoning to Gemini" :
                "Sending initial clinical assessment to Gemini";
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", logMessage, "INFO"));

            // Asynchronous API call
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                        "Conversation-aware API call failed: " + e.getMessage() + " - Using medical fallback", "WARNING"));
                    
                    LLMAnalysisResult fallbackResult = generateConversationAwareFallback(msg.sessionId, msg.symptoms, msg.context);
                    msg.replyTo.tell(fallbackResult);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            JsonNode json = jsonMapper.readTree(responseBody);
                            
                            JsonNode candidates = json.get("candidates");
                            if (candidates != null && candidates.isArray() && candidates.size() > 0) {
                                JsonNode firstCandidate = candidates.get(0);
                                JsonNode content = firstCandidate.get("content");
                                
                                if (content != null && content.has("parts")) {
                                    JsonNode parts = content.get("parts");
                                    if (parts.isArray() && parts.size() > 0) {
                                        String aiResponse = parts.get(0).get("text").asText();
                                        
                                        String successMessage = isConversationFollowUp ?
                                            "Conversation-aware clinical analysis successful" :
                                            "Enhanced clinical analysis successful";
                                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", successMessage, "INFO"));

                                        // PHASE A1: Parse conversation-aware medical response
                                        LLMAnalysisResult result = parseConversationAwareMedicalResponse(msg.sessionId, aiResponse, msg.symptoms, msg.context);
                                        msg.replyTo.tell(result);
                                        return;
                                    }
                                }
                            }
                            
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Unexpected conversation-aware API response format - using fallback", "WARNING"));
                            
                            LLMAnalysisResult fallbackResult = generateConversationAwareFallback(msg.sessionId, msg.symptoms, msg.context);
                            msg.replyTo.tell(fallbackResult);
                            
                        } catch (Exception ex) {
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Conversation-aware response parsing failed: " + ex.getMessage(), "ERROR"));
                            
                            LLMAnalysisResult fallbackResult = generateConversationAwareFallback(msg.sessionId, msg.symptoms, msg.context);
                            msg.replyTo.tell(fallbackResult);
                        }
                    } else {
                        String errorMsg = "HTTP " + response.code();
                        if (response.body() != null) {
                            String errorBody = response.body().string();
                            errorMsg += ": " + errorBody;
                        }
                        
                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                            "Conversation-aware API error: " + errorMsg + " - Using fallback", "WARNING"));
                        
                        LLMAnalysisResult fallbackResult = generateConversationAwareFallback(msg.sessionId, msg.symptoms, msg.context);
                        msg.replyTo.tell(fallbackResult);
                    }
                }
            });

        } catch (Exception e) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Conversation-aware request building failed: " + e.getMessage(), "ERROR"));
            msg.replyTo.tell(LLMAnalysisResult.failure(msg.sessionId, 
                "Conversation-aware clinical analysis system error: " + e.getMessage()));
        }

        return this;
    }

    /**
     * PHASE A1: Conversation-Aware Medical Reasoning Prompt
     */
    private String buildConversationAwareMedicalPrompt(String symptoms, String medicalContext) {
        // PHASE A1: Check if this includes conversation history
        boolean hasConversationHistory = medicalContext.contains("MEDICAL CONVERSATION HISTORY:");
        
        if (hasConversationHistory) {
            // CONVERSATION-AWARE PROMPT (for follow-up messages)
            return String.format("""
                You are an experienced emergency medicine physician conducting follow-up medical assessment.
                
                %s
                
                CURRENT PATIENT MESSAGE:
                "%s"
                
                MEDICAL CONVERSATION FRAMEWORK:
                This is a continuing medical conversation. Provide assessment that:
                1. References and builds upon previous symptoms/concerns discussed in conversation history
                2. Evaluates symptom progression, improvement, or new developments since last interaction
                3. Correlates current message with previous medical timeline and context
                4. Provides updated medical assessment based on complete symptom evolution
                5. Considers how symptoms may be progressing or responding to previous medical advice
                
                REQUIRED RESPONSE FORMAT:
                
                **CONVERSATION ASSESSMENT:**
                [How does the current message relate to our previous medical conversation? Any symptom 
                progression, improvement, new developments, or follow-up information provided?]
                
                **UPDATED CLINICAL ASSESSMENT:**
                [Updated medical analysis considering the complete conversation timeline and symptom evolution]
                
                **PROGRESSIVE DIFFERENTIAL DIAGNOSIS:**
                [Updated diagnostic considerations based on full symptom development over conversation]
                1. [Most likely condition considering progression] - [rationale including conversation timeline]
                2. [Alternative condition] - [rationale with conversation context]
                3. [Condition to monitor/rule out] - [rationale based on symptom evolution]
                
                **CONVERSATION-BASED RISK STRATIFICATION:**
                [Emergency/Urgent/Routine] - [Medical reasoning based on symptom progression and conversation timeline]
                
                **CLINICAL CORRELATION:**
                [How conversation history and current symptoms together inform medical decision-making and risk assessment]
                
                **FOLLOW-UP RECOMMENDATIONS:**
                [Specific next steps considering conversation progression and any symptom development patterns]
                
                CLASSIFICATION: [EMERGENCY/SELF_CARE/APPOINTMENT]
                SEVERITY: [HIGH/MODERATE/LOW]
                """, medicalContext, symptoms);
        } else {
            // INITIAL CONVERSATION PROMPT (for first messages - enhanced)
            return String.format("""
                You are an experienced emergency medicine physician conducting initial medical consultation.
                
                MEDICAL EVIDENCE BASE:
                %s
                
                PATIENT PRESENTATION:
                Chief Complaint: %s
                
                INITIAL CONSULTATION FRAMEWORK:
                This is the patient's initial presentation. Conduct systematic initial assessment and 
                consider what follow-up information might help complete the medical evaluation.
                
                REQUIRED RESPONSE FORMAT:
                
                **INITIAL CLINICAL ASSESSMENT:**
                [First impression medical analysis of symptom presentation based on evidence]
                
                **DIFFERENTIAL DIAGNOSIS:**
                1. [Most likely condition] - [initial medical rationale based on evidence]
                2. [Alternative condition to consider] - [rationale]
                3. [Serious condition to rule out] - [rationale for emergency exclusion]
                
                **RISK STRATIFICATION:**
                [Emergency/Urgent/Routine] - [Initial medical reasoning for classification based on evidence]
                
                **CLINICAL CORRELATION:**
                [How the provided medical evidence supports your initial assessment and recommendations]
                
                **INITIAL RECOMMENDATIONS:**
                [Immediate next steps for this presentation based on risk level]
                
                **FOLLOW-UP CONSIDERATIONS:**
                [What additional symptoms or information would be helpful to monitor or assess, if any]
                
                CLASSIFICATION: [EMERGENCY/SELF_CARE/APPOINTMENT]
                SEVERITY: [HIGH/MODERATE/LOW]
                """, medicalContext, symptoms);
        }
    }

    /**
     * PHASE A1: Enhanced response parsing with conversation awareness
     */
    private LLMAnalysisResult parseConversationAwareMedicalResponse(String sessionId, String aiResponse, String symptoms, String context) {
        try {
            boolean isConversationFollowUp = context.contains("MEDICAL CONVERSATION HISTORY:");
            
            // Extract sections based on response type
            String clinicalSection = "";
            String differentialDx = "";
            String riskStratification = "";
            String clinicalCorrelation = "";
            String recommendation = "";
            
            if (isConversationFollowUp) {
                // Extract conversation-specific sections
                String conversationAssessment = extractSection(aiResponse, "**CONVERSATION ASSESSMENT:**");
                clinicalSection = extractSection(aiResponse, "**UPDATED CLINICAL ASSESSMENT:**");
                differentialDx = extractSection(aiResponse, "**PROGRESSIVE DIFFERENTIAL DIAGNOSIS:**");
                riskStratification = extractSection(aiResponse, "**CONVERSATION-BASED RISK STRATIFICATION:**");
                recommendation = extractSection(aiResponse, "**FOLLOW-UP RECOMMENDATIONS:**");
                
                // Build comprehensive conversation-aware analysis
                StringBuilder comprehensiveAnalysis = new StringBuilder();
                
                if (!conversationAssessment.isEmpty()) {
                    comprehensiveAnalysis.append("**CONVERSATION ASSESSMENT:**\n").append(conversationAssessment).append("\n\n");
                }
                
                if (!clinicalSection.isEmpty()) {
                    comprehensiveAnalysis.append("**UPDATED CLINICAL ASSESSMENT:**\n").append(clinicalSection).append("\n\n");
                } else {
                    comprehensiveAnalysis.append("**CLINICAL ASSESSMENT:**\n").append(extractSection(aiResponse, "**CLINICAL ASSESSMENT:**")).append("\n\n");
                }
                
                if (!differentialDx.isEmpty()) {
                    comprehensiveAnalysis.append("**PROGRESSIVE DIFFERENTIAL DIAGNOSIS:**\n").append(differentialDx).append("\n\n");
                }
                
                if (!riskStratification.isEmpty()) {
                    comprehensiveAnalysis.append("**CONVERSATION-BASED RISK STRATIFICATION:**\n").append(riskStratification).append("\n\n");
                }
                
                clinicalSection = comprehensiveAnalysis.toString().trim();
                
            } else {
                // Extract initial consultation sections
                clinicalSection = extractSection(aiResponse, "**INITIAL CLINICAL ASSESSMENT:**");
                if (clinicalSection.isEmpty()) {
                    clinicalSection = extractSection(aiResponse, "**CLINICAL ASSESSMENT:**");
                }
                differentialDx = extractSection(aiResponse, "**DIFFERENTIAL DIAGNOSIS:**");
                riskStratification = extractSection(aiResponse, "**RISK STRATIFICATION:**");
                recommendation = extractSection(aiResponse, "**INITIAL RECOMMENDATIONS:**");
                if (recommendation.isEmpty()) {
                    recommendation = extractSection(aiResponse, "**RECOMMENDATION:**");
                }
                
                // Build comprehensive initial analysis
                StringBuilder comprehensiveAnalysis = new StringBuilder();
                
                if (!clinicalSection.isEmpty()) {
                    comprehensiveAnalysis.append("**CLINICAL ASSESSMENT:**\n").append(clinicalSection).append("\n\n");
                }
                
                if (!differentialDx.isEmpty()) {
                    comprehensiveAnalysis.append("**DIFFERENTIAL DIAGNOSIS:**\n").append(differentialDx).append("\n\n");
                }
                
                if (!riskStratification.isEmpty()) {
                    comprehensiveAnalysis.append("**RISK STRATIFICATION:**\n").append(riskStratification).append("\n\n");
                }
                
                clinicalSection = comprehensiveAnalysis.toString().trim();
            }
            
            clinicalCorrelation = extractSection(aiResponse, "**CLINICAL CORRELATION:**");
            if (!clinicalCorrelation.isEmpty()) {
                clinicalSection += "\n\n**CLINICAL CORRELATION:**\n" + clinicalCorrelation;
            }
            
            // Extract classification and severity
            String classification = extractSection(aiResponse, "CLASSIFICATION:");
            String severity = extractSection(aiResponse, "SEVERITY:");
            
            // Validate classification
            String finalClassification = classification.toUpperCase().trim();
            if (!finalClassification.contains("EMERGENCY") && 
                !finalClassification.contains("SELF_CARE") && 
                !finalClassification.contains("APPOINTMENT")) {
                finalClassification = "APPOINTMENT"; // Safe default
            }
            
            // Validate severity
            String finalSeverity = severity.toUpperCase().trim();
            if (!finalSeverity.contains("HIGH") && 
                !finalSeverity.contains("MODERATE") && 
                !finalSeverity.contains("LOW")) {
                finalSeverity = "MODERATE"; // Safe default
            }
            
            // Use full clinical section as analysis
            String finalAnalysis = clinicalSection.isEmpty() ? aiResponse : clinicalSection;
            String finalRecommendation = recommendation.isEmpty() ? clinicalSection : recommendation;
            
            String completionMessage = isConversationFollowUp ?
                "PHASE A1: Conversation-aware clinical assessment completed" :
                "PHASE 1A: Doctor-level clinical assessment completed";
            logger.tell(new LogEvent(sessionId, "LLMActor", completionMessage, "INFO"));
            
            return LLMAnalysisResult.success(sessionId, finalAnalysis, finalSeverity, finalRecommendation);
            
        } catch (Exception e) {
            logger.tell(new LogEvent(sessionId, "LLMActor", 
                "Conversation-aware parsing failed, using fallback: " + e.getMessage(), "WARNING"));
            
            return generateConversationAwareFallback(sessionId, symptoms, context);
        }
    }

    /**
     * PHASE A1: Helper method to extract structured sections from LLM response
     */
    private String extractSection(String response, String sectionHeader) {
        try {
            int startIndex = response.indexOf(sectionHeader);
            if (startIndex == -1) return "";
            
            startIndex += sectionHeader.length();
            
            // Find next section or end
            int endIndex = response.indexOf("\n\n**", startIndex);
            if (endIndex == -1) {
                endIndex = response.indexOf("\nCLASSIFICATION:", startIndex);
            }
            if (endIndex == -1) {
                endIndex = response.indexOf("\nSEVERITY:", startIndex);
            }
            if (endIndex == -1) {
                endIndex = response.length();
            }
            
            return response.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * PHASE A1: Conversation-aware medical fallback
     */
    private LLMAnalysisResult generateConversationAwareFallback(String sessionId, String symptoms, String context) {
        String symptomsLower = symptoms.toLowerCase();
        boolean hasConversationHistory = context.contains("MEDICAL CONVERSATION HISTORY:");
        
        // Check for non-medical inputs first
        if (isNonMedicalInput(symptomsLower)) {
            return LLMAnalysisResult.success(sessionId, 
                "**NON-MEDICAL INPUT DETECTED:**\nI'm a medical triage assistant designed to help with health symptoms and medical concerns.", 
                "NON_MEDICAL", 
                "Please describe your medical symptoms such as pain, discomfort, fever, or other physical health concerns.");
        }
        
        String clinicalAssessment = "";
        String differentialDx = "";
        String riskLevel = "";
        String severity = "";
        String recommendation = "";
        
        if (hasConversationHistory) {
            // CONVERSATION FOLLOW-UP FALLBACK
            clinicalAssessment = "**CONVERSATION ASSESSMENT:**\nFollow-up information provided in ongoing medical conversation. Assessment updated based on additional symptom details.\n\n" +
                               "**UPDATED CLINICAL ASSESSMENT:**\nSymptom progression and additional details from conversation provide enhanced clinical picture for assessment.";
        } else {
            // INITIAL CONSULTATION FALLBACK  
            clinicalAssessment = "**INITIAL CLINICAL ASSESSMENT:**\nInitial symptom presentation requires systematic medical evaluation.";
        }
        
        // Generate condition-specific assessment (same logic as before)
        if (hasEmergencySymptoms(symptomsLower)) {
            if (symptomsLower.contains("chest pain")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Acute coronary syndrome - High-risk presentation\n2. Pulmonary embolism - Consider with associated symptoms\n3. Aortic dissection - Critical condition to exclude";
                recommendation = "Call 911 immediately. Do not drive yourself. Consider aspirin if no allergies.";
            } else if (symptomsLower.contains("breathing") || symptomsLower.contains("shortness")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Acute respiratory failure - Severe presentation\n2. Pulmonary edema - Consider cardiac causes\n3. Asthma exacerbation - Bronchospasm pattern";
                recommendation = "Call 911 immediately. Sit upright, use rescue inhaler if available.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Medical emergency - Based on symptom severity\n2. Acute condition - Requires immediate evaluation\n3. Systemic emergency - Needs urgent intervention";
                recommendation = "Call 911 or go to emergency department immediately.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nEmergency - Immediate medical intervention required";
            severity = "HIGH";
            
        } else if (hasSelfCareSymptoms(symptomsLower)) {
            clinicalAssessment += "\n\n**CLINICAL ASSESSMENT:**\nSymptom pattern suggests minor condition manageable with home care measures.";
            
            if (symptomsLower.contains("headache")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Tension headache - Most common pattern\n2. Viral syndrome - Systemic symptoms\n3. Dehydration - Consider fluid status";
                recommendation = "Rest in quiet, dark room. Hydration and OTC analgesics as appropriate.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Minor condition - Self-limiting presentation\n2. Viral syndrome - Common illness pattern\n3. Functional symptoms - Lifestyle or stress related";
                recommendation = "Home care with symptom monitoring and supportive measures.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nRoutine - Home management appropriate with monitoring";
            severity = "LOW";
            
        } else {
            clinicalAssessment += "\n\n**CLINICAL ASSESSMENT:**\nSymptom presentation requires medical evaluation for proper diagnosis and treatment planning.";
            
            if (symptomsLower.contains("back pain")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Mechanical back pain - Most common cause\n2. Muscle strain - Activity-related pattern\n3. Disc pathology - Consider with neurological symptoms";
                recommendation = "Schedule appointment with primary care. Conservative management with activity modification.";
            } else if (symptomsLower.contains("vomiting") || symptomsLower.contains("nausea")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Viral gastroenteritis - Most common cause\n2. Food poisoning - Consider recent exposure\n3. Medication reaction - Review recent changes";
                recommendation = "Clear liquids, BRAT diet, rest. Appointment if persistent beyond 24-48 hours.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Medical condition - Requires clinical evaluation\n2. Systemic symptoms - Needs diagnostic workup\n3. Chronic condition - May require ongoing care";
                recommendation = "Schedule appointment with healthcare provider for evaluation and diagnosis.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nRoutine - Non-urgent medical evaluation appropriate";
            severity = "MODERATE";
        }
        
        // Build comprehensive conversation-aware analysis
        String comprehensiveAnalysis = clinicalAssessment + "\n\n" + 
                                     differentialDx + "\n\n" + 
                                     riskLevel + "\n\n" +
                                     "**CLINICAL CORRELATION:**\nAssessment based on conversation-aware clinical reasoning protocols.";
        
        String completionMessage = hasConversationHistory ?
            "PHASE A1: Conversation-aware medical fallback completed" :
            "PHASE 1A: Enhanced medical fallback completed";
        logger.tell(new LogEvent(sessionId, "LLMActor", completionMessage, "INFO"));
        
        return LLMAnalysisResult.success(sessionId, comprehensiveAnalysis, severity, recommendation);
    }

    private boolean isNonMedicalInput(String symptomsLower) {
        String[] nonMedicalTerms = {
            "capital", "weather", "hello", "hi", "what is", "who is", "how to",
            "calculate", "recipe", "movie", "book", "song", "president", "history"
        };
        
        for (String term : nonMedicalTerms) {
            if (symptomsLower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEmergencySymptoms(String symptomsLower) {
        // Cardiac emergencies
        if (symptomsLower.contains("chest pain") && 
            (symptomsLower.contains("radiation") || symptomsLower.contains("arm") || 
             symptomsLower.contains("sweating") || symptomsLower.contains("shortness"))) {
            return true;
        }
        
        // Respiratory emergencies
        if (symptomsLower.contains("severe") && 
            (symptomsLower.contains("breathing") || symptomsLower.contains("shortness of breath"))) {
            return true;
        }
        
        // Neurological emergencies
        if (symptomsLower.contains("stroke") || symptomsLower.contains("seizure") ||
            (symptomsLower.contains("headache") && symptomsLower.contains("severe"))) {
            return true;
        }
        
        // General emergency indicators
        if (symptomsLower.contains("emergency") || symptomsLower.contains("severe") ||
            symptomsLower.contains("unconscious") || symptomsLower.contains("bleeding")) {
            return true;
        }
        
        return false;
    }

    private boolean hasSelfCareSymptoms(String symptomsLower) {
        return symptomsLower.contains("mild") || symptomsLower.contains("minor") ||
               symptomsLower.contains("headache") || symptomsLower.contains("runny nose") ||
               symptomsLower.contains("sore throat") || symptomsLower.contains("cough") ||
               symptomsLower.contains("cold") || symptomsLower.contains("tired");
    }
}