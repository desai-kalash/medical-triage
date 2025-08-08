package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced TriageRouterActor - Core system orchestrator with Vector Database
 * Demonstrates ASK and FORWARD communication patterns
 * Now includes vector similarity search for grounded medical knowledge
 */
public class TriageRouterActor extends AbstractBehavior<TriageCommand> {

    private final ActorRef<LLMCommand> llmActor;
    private final ActorRef<RetrievalCommand> retrievalActor;
    private final ActorRef<LogCommand> logger;
    private final ActorRef<CareCommand> emergencyCare;
    private final ActorRef<CareCommand> selfCare;
    private final ActorRef<CareCommand> appointmentCare;

    // Internal messages for handling async results
    public static class ProcessingError implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final String error;

        public ProcessingError(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo, String error) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.error = error;
        }
    }

    public static class ProcessingComplete implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final LLMAnalysisResult llmResult;

        public ProcessingComplete(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo, LLMAnalysisResult llmResult) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.llmResult = llmResult;
        }
    }

    // NEW: Vector retrieval completion message
    public static class VectorRetrievalComplete implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final Retrieved retrievalResult;

        public VectorRetrievalComplete(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo, Retrieved retrievalResult) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.retrievalResult = retrievalResult;
        }
    }

    // NEW: Vector retrieval response handler
    public static class VectorRetrievalHandler extends AbstractBehavior<Retrieved> {
        private final String sessionId;
        private final String symptoms;
        private final ActorRef<TriageResponse> originalReplyTo;
        private final ActorRef<TriageCommand> triageRouter;

        public static Behavior<Retrieved> create(String sessionId, String symptoms, 
                                                ActorRef<TriageResponse> originalReplyTo,
                                                ActorRef<TriageCommand> triageRouter) {
            return Behaviors.setup(context -> new VectorRetrievalHandler(context, sessionId, symptoms, originalReplyTo, triageRouter));
        }

        private VectorRetrievalHandler(ActorContext<Retrieved> context, String sessionId, String symptoms,
                                      ActorRef<TriageResponse> originalReplyTo, ActorRef<TriageCommand> triageRouter) {
            super(context);
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.originalReplyTo = originalReplyTo;
            this.triageRouter = triageRouter;
        }

        @Override
        public Receive<Retrieved> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Retrieved.class, this::onRetrieved)
                    .build();
        }

        private Behavior<Retrieved> onRetrieved(Retrieved result) {
            if (result.success) {
                triageRouter.tell(new VectorRetrievalComplete(sessionId, symptoms, originalReplyTo, result));
            } else {
                triageRouter.tell(new ProcessingError(sessionId, symptoms, originalReplyTo, "Vector retrieval failed"));
            }
            return Behaviors.stopped();
        }
    }

    // LLM response handler (same as before)
    public static class LLMResponseHandler extends AbstractBehavior<LLMAnalysisResult> {
        private final String sessionId;
        private final String symptoms;
        private final ActorRef<TriageResponse> originalReplyTo;
        private final ActorRef<TriageCommand> triageRouter;

        public static Behavior<LLMAnalysisResult> create(String sessionId, String symptoms,
                                                        ActorRef<TriageResponse> originalReplyTo,
                                                        ActorRef<TriageCommand> triageRouter) {
            return Behaviors.setup(context -> new LLMResponseHandler(context, sessionId, symptoms, originalReplyTo, triageRouter));
        }

        private LLMResponseHandler(ActorContext<LLMAnalysisResult> context, String sessionId, String symptoms,
                                  ActorRef<TriageResponse> originalReplyTo, ActorRef<TriageCommand> triageRouter) {
            super(context);
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.originalReplyTo = originalReplyTo;
            this.triageRouter = triageRouter;
        }

        @Override
        public Receive<LLMAnalysisResult> createReceive() {
            return newReceiveBuilder()
                    .onMessage(LLMAnalysisResult.class, this::onLLMResult)
                    .build();
        }

        private Behavior<LLMAnalysisResult> onLLMResult(LLMAnalysisResult result) {
            triageRouter.tell(new ProcessingComplete(sessionId, symptoms, originalReplyTo, result));
            return Behaviors.stopped();
        }
    }

    public static Behavior<TriageCommand> create(
            ActorRef<LLMCommand> llmActor,
            ActorRef<RetrievalCommand> retrievalActor,
            ActorRef<LogCommand> logger,
            ActorRef<CareCommand> emergencyCare,
            ActorRef<CareCommand> selfCare,
            ActorRef<CareCommand> appointmentCare) {
        return Behaviors.setup(context -> 
            new TriageRouterActor(context, llmActor, retrievalActor, logger, 
                                emergencyCare, selfCare, appointmentCare));
    }

    private TriageRouterActor(ActorContext<TriageCommand> context,
                             ActorRef<LLMCommand> llmActor,
                             ActorRef<RetrievalCommand> retrievalActor,
                             ActorRef<LogCommand> logger,
                             ActorRef<CareCommand> emergencyCare,
                             ActorRef<CareCommand> selfCare,
                             ActorRef<CareCommand> appointmentActor) {
        super(context);
        this.llmActor = llmActor;
        this.retrievalActor = retrievalActor;
        this.logger = logger;
        this.emergencyCare = emergencyCare;
        this.selfCare = selfCare;
        this.appointmentCare = appointmentActor;
        
        getContext().getLog().info("🔀 TriageRouterActor initialized with vector database support");
    }

    @Override
    public Receive<TriageCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessSymptoms.class, this::onProcessSymptoms)
                .onMessage(VectorRetrievalComplete.class, this::onVectorRetrievalComplete)  // NEW
                .onMessage(ProcessingError.class, this::onProcessingError)
                .onMessage(ProcessingComplete.class, this::onProcessingComplete)
                .build();
    }

    private Behavior<TriageCommand> onProcessSymptoms(ProcessSymptoms msg) {
        getContext().getLog().info("🔀 Processing symptoms for session [{}]: {}", 
            msg.sessionId, msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Starting triage process with vector retrieval", "INFO"));

        // STEP 1: VECTOR SIMILARITY SEARCH - Get relevant medical knowledge
        ActorRef<Retrieved> retrievalHandler = getContext().spawn(
            VectorRetrievalHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, getContext().getSelf()),
            "vector-retrieval-" + msg.sessionId
        );

        // NEW: Send vector retrieval request (uses semantic similarity)
        retrievalActor.tell(new Retrieve(msg.sessionId, msg.symptoms, 5, retrievalHandler));

        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Vector similarity search initiated", "DEBUG"));

        return this;
    }

    // NEW: Handle vector retrieval completion
    private Behavior<TriageCommand> onVectorRetrievalComplete(VectorRetrievalComplete msg) {
        // Build enriched medical context from vector search results
        String enrichedContext = buildEnrichedContext(msg.retrievalResult.chunks, msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Vector retrieval completed, asking LLM with enriched context", "DEBUG"));
        
        // STEP 2: Ask LLM with enriched medical context
        ActorRef<LLMAnalysisResult> llmHandler = getContext().spawn(
            LLMResponseHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, getContext().getSelf()),
            "llm-handler-" + msg.sessionId
        );

        // Send enriched prompt to LLM
        llmActor.tell(new AnalyzeSymptoms(msg.sessionId, msg.symptoms, enrichedContext, llmHandler));

        return this;
    }

    /**
     * NEW: Build enriched medical context from vector search results
     */
    private String buildEnrichedContext(List<RetrievedChunk> chunks, String sessionId) {
        if (chunks.isEmpty()) {
            logger.tell(new LogEvent(sessionId, "TriageRouter", 
                "No medical knowledge chunks available - using general context", "WARNING"));
            return "General medical knowledge - no specific guidance found for these symptoms.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("MEDICAL KNOWLEDGE BASE CONTEXT:\n\n");
        
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            context.append(String.format("[%d] %s (Source: %s, Relevance: %.3f)\n%s\n\n",
                i + 1, chunk.category.toUpperCase(), chunk.sourceName, chunk.score, chunk.text));
        }
        
        // Log which chunks were used for traceability
        String chunkInfo = chunks.stream()
            .map(c -> String.format("%s(%.3f)", c.id, c.score))
            .collect(Collectors.joining(", "));
        
        logger.tell(new LogEvent(sessionId, "TriageRouter", 
            "Using medical knowledge: " + chunkInfo, "INFO"));
        
        // Log sources for medical traceability
        String sources = chunks.stream()
            .map(c -> c.sourceName)
            .distinct()
            .collect(Collectors.joining(", "));
        
        logger.tell(new LogEvent(sessionId, "TriageRouter", 
            "Medical sources consulted: " + sources, "INFO"));
        
        return context.toString();
    }

    private Behavior<TriageCommand> onProcessingError(ProcessingError msg) {
        getContext().getLog().error("❌ Processing failed for session [{}]: {}", 
            msg.sessionId, msg.error);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Processing failed: " + msg.error, "ERROR"));
        
        msg.replyTo.tell(new TriageResponse(msg.sessionId, msg.symptoms, 
            "error", "System temporarily unavailable", "unknown", false));
        
        return this;
    }

    private Behavior<TriageCommand> onProcessingComplete(ProcessingComplete msg) {
        if (!msg.llmResult.success) {
            logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
                "LLM analysis failed: " + msg.llmResult.errorMessage, "ERROR"));
            
            msg.replyTo.tell(new TriageResponse(msg.sessionId, msg.symptoms, 
                "error", "Analysis failed", "unknown", false));
            return this;
        }

        // STEP 3: Route based on analysis and use FORWARD pattern
        String classification = classifyUrgency(msg.llmResult);
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Classification: " + classification, "INFO"));

        routeToSpecializedCare(msg.sessionId, msg.symptoms, msg.replyTo, msg.llmResult, classification);
        return this;
    }

    private void routeToSpecializedCare(String sessionId, String symptoms, 
                                      ActorRef<TriageResponse> replyTo,
                                      LLMAnalysisResult llmResult, 
                                      String classification) {
        // Create care message
        HandleTriageCase careMsg = new HandleTriageCase(
            sessionId,
            symptoms,
            llmResult.analysis,
            llmResult.severity,
            replyTo
        );

        // FORWARD PATTERN: Route to appropriate care actor while preserving original sender
        ActorRef<CareCommand> targetActor;
        String careType;
        
        switch (classification.toLowerCase()) {
            case "non-medical":
                // Handle non-medical inputs directly without routing to care actors
                logger.tell(new LogEvent(sessionId, "TriageRouter", 
                    "Non-medical input detected", "INFO"));
                
                TriageResponse nonMedicalResponse = new TriageResponse(
                    sessionId,
                    symptoms,
                    "NON-MEDICAL",
                    "⚠️ I'm a medical triage assistant designed to help with health symptoms and concerns.\n\n" +
                    "Please describe your medical symptoms, such as:\n" +
                    "• Pain, discomfort, or unusual sensations\n" +
                    "• Changes in how you feel physically\n" +
                    "• Health concerns or symptoms you're experiencing\n\n" +
                    "Examples: 'I have chest pain', 'headache for 3 days', 'fever and cough'\n\n" +
                    "If you need help with non-medical questions, please consult other resources.",
                    "NON_MEDICAL",
                    true
                );
                
                replyTo.tell(nonMedicalResponse);
                return; // Don't route to care actors
                
            case "emergency":
                targetActor = emergencyCare;
                careType = "Emergency";
                break;
            case "self-care":
                targetActor = selfCare;
                careType = "Self-Care";
                break;
            case "appointment":
            default:
                targetActor = appointmentCare;
                careType = "Appointment";
                break;
        }

        logger.tell(new LogEvent(sessionId, "TriageRouter", 
            "FORWARD pattern to " + careType + "Actor", "DEBUG"));

        getContext().getLog().info("🎯 FORWARD to {}Actor for session [{}]", 
            careType, sessionId);

        // FORWARD maintains original sender context
        targetActor.tell(careMsg);
    }

    private String classifyUrgency(LLMAnalysisResult llmResult) {
        String analysis = llmResult.analysis.toLowerCase();
        String severity = llmResult.severity.toLowerCase();
        String recommendation = llmResult.recommendation.toLowerCase();
        
        // Check for non-medical input first
        if (severity.contains("non_medical") || severity.equals("non_medical")) {
            return "non-medical";
        }
        
        // Emergency classification - be more specific
        if (severity.contains("high") || severity.contains("critical") ||
            analysis.contains("call 911") || analysis.contains("emergency services") ||
            analysis.contains("immediate") || analysis.contains("urgent") ||
            recommendation.contains("call 911") || recommendation.contains("emergency room") ||
            analysis.contains("heart attack") || analysis.contains("stroke") ||
            analysis.contains("severe bleeding") || analysis.contains("surgical emergency")) {
            return "emergency";
        }
        
        // Self-care classification
        if (severity.contains("low") || severity.contains("mild") ||
            analysis.contains("home care") || analysis.contains("rest") ||
            analysis.contains("over-the-counter") || analysis.contains("self-treat") ||
            recommendation.contains("home remedies") || recommendation.contains("rest")) {
            return "self-care";
        }
        
        // Appointment classification (more specific)
        if (analysis.contains("schedule") || analysis.contains("appointment") ||
            analysis.contains("healthcare provider") || analysis.contains("doctor") ||
            recommendation.contains("schedule") || recommendation.contains("appointment") ||
            severity.contains("moderate") || analysis.contains("persistent") ||
            analysis.contains("chronic") || analysis.contains("recurring")) {
            return "appointment";
        }
        
        // Default to appointment for unclear cases (safer)
        return "appointment";
    }
}