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
 * PHASE 1A: Enhanced LLMActor - Doctor-Level Medical Intelligence
 * Now provides structured clinical assessment with differential diagnosis
 * Transforms from basic analysis to professional medical reasoning
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
            "Starting enhanced clinical analysis", "INFO"));

        if (apiKey == null || apiKey.isEmpty()) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "API key missing - using medical fallback", "ERROR"));
            msg.replyTo.tell(generateMedicalFallbackAnalysis(msg.sessionId, msg.symptoms));
            return this;
        }

        // PHASE 1A: Build enhanced medical reasoning prompt
        String enhancedMedicalPrompt = buildDoctorLevelPrompt(msg.symptoms, msg.context);
        
        // Log prompt preview for debugging
        String promptPreview = enhancedMedicalPrompt.length() > 200 ? 
            enhancedMedicalPrompt.substring(0, 200) + "..." : enhancedMedicalPrompt;
        getContext().getLog().debug("🔬 PHASE 1A: Enhanced prompt preview: {}", promptPreview);
        
        try {
            // Build enhanced Gemini request
            ObjectNode requestPayload = jsonMapper.createObjectNode();
            
            ObjectNode content = jsonMapper.createObjectNode();
            ObjectNode textPart = jsonMapper.createObjectNode();
            textPart.put("text", enhancedMedicalPrompt);
            
            content.putArray("parts").add(textPart);
            requestPayload.putArray("contents").add(content);
            
            // PHASE 1A: Enhanced generation config for medical reasoning
            ObjectNode generationConfig = jsonMapper.createObjectNode();
            generationConfig.put("temperature", 0.3);      // Lower for consistent medical reasoning
            generationConfig.put("maxOutputTokens", 1000); // More tokens for detailed assessment
            generationConfig.put("topP", 0.8);
            generationConfig.put("topK", 40);
            requestPayload.set("generationConfig", generationConfig);

            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

            Request request = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestPayload.toString(), 
                    MediaType.parse("application/json")))
                .build();

            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Sending enhanced clinical reasoning request to Gemini", "INFO"));

            // Asynchronous API call
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                        "Enhanced API call failed: " + e.getMessage() + " - Using medical fallback", "WARNING"));
                    
                    LLMAnalysisResult fallbackResult = generateMedicalFallbackAnalysis(msg.sessionId, msg.symptoms);
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
                                        
                                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                            "Enhanced clinical analysis successful", "INFO"));

                                        // PHASE 1A: Parse enhanced medical response
                                        LLMAnalysisResult result = parseEnhancedMedicalResponse(msg.sessionId, aiResponse, msg.symptoms);
                                        msg.replyTo.tell(result);
                                        return;
                                    }
                                }
                            }
                            
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Unexpected enhanced API response format - using fallback", "WARNING"));
                            
                            LLMAnalysisResult fallbackResult = generateMedicalFallbackAnalysis(msg.sessionId, msg.symptoms);
                            msg.replyTo.tell(fallbackResult);
                            
                        } catch (Exception ex) {
                            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                                "Enhanced response parsing failed: " + ex.getMessage(), "ERROR"));
                            
                            LLMAnalysisResult fallbackResult = generateMedicalFallbackAnalysis(msg.sessionId, msg.symptoms);
                            msg.replyTo.tell(fallbackResult);
                        }
                    } else {
                        String errorMsg = "HTTP " + response.code();
                        if (response.body() != null) {
                            String errorBody = response.body().string();
                            errorMsg += ": " + errorBody;
                        }
                        
                        logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                            "Enhanced API error: " + errorMsg + " - Using fallback", "WARNING"));
                        
                        LLMAnalysisResult fallbackResult = generateMedicalFallbackAnalysis(msg.sessionId, msg.symptoms);
                        msg.replyTo.tell(fallbackResult);
                    }
                }
            });

        } catch (Exception e) {
            logger.tell(new LogEvent(msg.sessionId, "LLMActor", 
                "Enhanced request building failed: " + e.getMessage(), "ERROR"));
            msg.replyTo.tell(LLMAnalysisResult.failure(msg.sessionId, 
                "Clinical analysis system error: " + e.getMessage()));
        }

        return this;
    }

    /**
     * PHASE 1A: Enhanced Doctor-Level Medical Reasoning Prompt
     */
    private String buildDoctorLevelPrompt(String symptoms, String medicalContext) {
        return String.format("""
            You are an experienced emergency medicine physician conducting systematic clinical assessment.
            
            MEDICAL EVIDENCE BASE:
            %s
            
            PATIENT PRESENTATION:
            Chief Complaint: %s
            
            CLINICAL REASONING FRAMEWORK:
            Conduct systematic medical assessment using ONLY the provided medical evidence base.
            
            REQUIRED RESPONSE FORMAT (use exactly this structure):
            
            **CLINICAL ASSESSMENT:**
            [Provide 2-3 sentence medical analysis of what this symptom pattern suggests. 
            Be specific about the most likely medical explanations based on evidence.]
            
            **DIFFERENTIAL DIAGNOSIS:**
            1. [Most likely condition] - [brief medical rationale based on evidence]
            2. [Alternative condition to consider] - [brief rationale]
            3. [Serious condition to rule out] - [brief rationale]
            
            **RISK STRATIFICATION:**
            [Emergency/Urgent/Routine] - [Medical reasoning for this classification based on evidence]
            
            **CLINICAL CORRELATION:**
            [How the provided medical evidence supports your assessment. Reference specific 
            guidelines or protocols from the evidence base.]
            
            **RECOMMENDATION:**
            [Specific next steps based on risk level and medical evidence]
            
            CRITICAL INSTRUCTIONS:
            - Use ONLY the provided medical evidence base for your reasoning
            - Be specific and avoid generic statements like 'can have various causes'
            - Reference the medical sources provided in your clinical correlation
            - If symptoms suggest emergency, clearly state to call emergency services
            - Maintain professional medical terminology while being understandable
            - Base ALL conclusions on the provided medical evidence
            
            CLASSIFICATION: [EMERGENCY/SELF_CARE/APPOINTMENT]
            SEVERITY: [HIGH/MODERATE/LOW]
            """, 
            medicalContext != null && !medicalContext.trim().isEmpty() ? 
                medicalContext : "Limited medical context available for assessment", 
            symptoms);
    }

    /**
     * PHASE 1A: Enhanced response parsing - FIXED symptoms parameter
     */
    private LLMAnalysisResult parseEnhancedMedicalResponse(String sessionId, String aiResponse, String symptoms) {
        try {
            // Extract structured sections from doctor-level response
            String clinicalAssessment = extractSection(aiResponse, "**CLINICAL ASSESSMENT:**");
            String differentialDx = extractSection(aiResponse, "**DIFFERENTIAL DIAGNOSIS:**");
            String riskStratification = extractSection(aiResponse, "**RISK STRATIFICATION:**");
            String clinicalCorrelation = extractSection(aiResponse, "**CLINICAL CORRELATION:**");
            String recommendation = extractSection(aiResponse, "**RECOMMENDATION:**");
            String classification = extractSection(aiResponse, "CLASSIFICATION:");
            String severity = extractSection(aiResponse, "SEVERITY:");
            
            // Build comprehensive medical analysis
            StringBuilder comprehensiveAnalysis = new StringBuilder();
            
            if (!clinicalAssessment.isEmpty()) {
                comprehensiveAnalysis.append("**CLINICAL ASSESSMENT:**\n").append(clinicalAssessment).append("\n\n");
            }
            
            if (!differentialDx.isEmpty()) {
                comprehensiveAnalysis.append("**DIFFERENTIAL DIAGNOSIS:**\n").append(differentialDx).append("\n\n");
            }
            
            if (!riskStratification.isEmpty()) {
                comprehensiveAnalysis.append("**RISK STRATIFICATION:**\n").append(riskStratification).append("\n\n");
            }
            
            if (!clinicalCorrelation.isEmpty()) {
                comprehensiveAnalysis.append("**CLINICAL CORRELATION:**\n").append(clinicalCorrelation).append("\n\n");
            }
            
            // Use the full comprehensive analysis as the main analysis
            String finalAnalysis = comprehensiveAnalysis.toString().trim();
            if (finalAnalysis.isEmpty()) {
                finalAnalysis = aiResponse; // Fallback to full response
            }
            
            // Extract and validate classification
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
            
            // Use recommendation or clinical assessment
            String finalRecommendation = !recommendation.isEmpty() ? recommendation : clinicalAssessment;
            
            logger.tell(new LogEvent(sessionId, "LLMActor", 
                "PHASE 1A: Doctor-level clinical assessment completed", "INFO"));
            
            return LLMAnalysisResult.success(sessionId, finalAnalysis, finalSeverity, finalRecommendation);
            
        } catch (Exception e) {
            logger.tell(new LogEvent(sessionId, "LLMActor", 
                "Enhanced parsing failed, using structured fallback: " + e.getMessage(), "WARNING"));
            
            // FIXED: Pass symptoms parameter to fallback
            return generateMedicalFallbackAnalysis(sessionId, symptoms);
        }
    }

    /**
     * PHASE 1A: Helper method to extract structured sections from LLM response
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
     * PHASE 1A: Enhanced medical fallback with clinical reasoning
     */
    private LLMAnalysisResult generateMedicalFallbackAnalysis(String sessionId, String symptoms) {
        String symptomsLower = symptoms.toLowerCase();
        
        // Check for non-medical inputs first
        if (isNonMedicalInput(symptomsLower)) {
            return LLMAnalysisResult.success(sessionId, 
                "**NON-MEDICAL INPUT DETECTED:**\nI'm a medical triage assistant designed to help with health symptoms and medical concerns.", 
                "NON_MEDICAL", 
                "Please describe your medical symptoms such as pain, discomfort, fever, or other physical health concerns.");
        }
        
        // PHASE 1A: Medical fallback with clinical reasoning structure
        String clinicalAssessment = "";
        String differentialDx = "";
        String riskLevel = "";
        String severity = "";
        String recommendation = "";
        
        // Emergency presentations
        if (hasEmergencySymptoms(symptomsLower)) {
            clinicalAssessment = "**CLINICAL ASSESSMENT:**\nSymptom constellation suggests potential medical emergency requiring immediate evaluation. High-risk presentation based on symptom pattern.";
            
            if (symptomsLower.contains("chest pain")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Acute coronary syndrome - Classic presentation pattern\n2. Pulmonary embolism - Consider with chest pain and dyspnea\n3. Aortic dissection - High-risk chest pain presentation";
                recommendation = "Call 911 immediately. Do not drive yourself. Consider aspirin if no allergies.";
            } else if (symptomsLower.contains("breathing") || symptomsLower.contains("shortness")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Acute respiratory failure - Severe dyspnea pattern\n2. Pulmonary edema - Consider with breathing difficulty\n3. Asthma exacerbation - Bronchospasm presentation";
                recommendation = "Call 911 immediately. Sit upright, loosen clothing. Use rescue inhaler if available.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Medical emergency - Based on symptom severity\n2. Acute organ dysfunction - Requires immediate evaluation\n3. Systemic condition - Needs urgent assessment";
                recommendation = "Call 911 or go to emergency department immediately.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nEmergency - Immediate medical intervention required";
            severity = "HIGH";
            
        } else if (hasSelfCareSymptoms(symptomsLower)) {
            // Self-care presentations
            clinicalAssessment = "**CLINICAL ASSESSMENT:**\nSymptom pattern suggests minor condition likely manageable with conservative home care measures.";
            
            if (symptomsLower.contains("headache")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Tension headache - Most common presentation\n2. Viral syndrome - Associated with systemic symptoms\n3. Dehydration - Consider fluid status";
                recommendation = "Rest in quiet, dark room. Adequate hydration. OTC analgesics as directed.";
            } else if (symptomsLower.contains("cold") || symptomsLower.contains("cough") || symptomsLower.contains("runny")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Viral upper respiratory infection - Most likely\n2. Common cold - Self-limiting course\n3. Allergic rhinitis - Consider environmental triggers";
                recommendation = "Rest, increased fluids, supportive care. Monitor for bacterial complications.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Minor condition - Self-limiting presentation\n2. Viral syndrome - Common viral illness\n3. Functional disorder - Stress or lifestyle related";
                recommendation = "Home care with symptom monitoring. Rest and supportive measures.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nRoutine - Home management appropriate with monitoring";
            severity = "LOW";
            
        } else {
            // Appointment-level presentations
            clinicalAssessment = "**CLINICAL ASSESSMENT:**\nSymptom presentation requires medical evaluation to determine underlying cause and appropriate treatment plan.";
            
            if (symptomsLower.contains("back pain")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Mechanical back pain - Most common cause\n2. Muscle strain - Activity-related injury\n3. Disc pathology - Consider with radicular symptoms";
                recommendation = "Schedule appointment with primary care. Activity modification, NSAIDs as appropriate.";
            } else if (symptomsLower.contains("vomiting") || symptomsLower.contains("nausea")) {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Viral gastroenteritis - Most common cause\n2. Food poisoning - Consider recent food exposure\n3. Medication side effect - Review recent medications";
                recommendation = "Clear liquids, BRAT diet, rest. Schedule appointment if persistent beyond 24-48 hours.";
            } else {
                differentialDx = "**DIFFERENTIAL DIAGNOSIS:**\n1. Functional disorder - Requires clinical evaluation\n2. Systemic condition - Needs diagnostic workup\n3. Chronic condition - May require ongoing management";
                recommendation = "Schedule appointment with healthcare provider for proper evaluation and diagnosis.";
            }
            
            riskLevel = "**RISK STRATIFICATION:**\nRoutine - Non-urgent medical evaluation appropriate";
            severity = "MODERATE";
        }
        
        // Build comprehensive fallback analysis
        String comprehensiveAnalysis = clinicalAssessment + "\n\n" + 
                                     differentialDx + "\n\n" + 
                                     riskLevel + "\n\n" +
                                     "**CLINICAL CORRELATION:**\nAssessment based on clinical reasoning fallback protocols when enhanced API unavailable.";
        
        logger.tell(new LogEvent(sessionId, "LLMActor", 
            "PHASE 1A: Enhanced medical fallback analysis completed", "INFO"));
        
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