package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * PHASE A1: Enhanced TriageRouterActor - Core system orchestrator with Conversation Memory
 * Now includes conversation awareness for multi-turn medical consultations
 * Demonstrates ASK and FORWARD communication patterns with session history
 */
public class TriageRouterActor extends AbstractBehavior<TriageCommand> {

    private final ActorRef<LLMCommand> llmActor;
    private final ActorRef<RetrievalCommand> retrievalActor;
    private final ActorRef<LogCommand> logger;
    private final ActorRef<CareCommand> emergencyCare;
    private final ActorRef<CareCommand> selfCare;
    private final ActorRef<CareCommand> appointmentCare;
    private final ActorRef<SessionCommand> sessionActor;  // PHASE A1: Conversation memory

    // PHASE A1: Conversation-aware processing message
    public static class ConversationContext implements TriageCommand {
        public final String sessionId;
        public final String currentSymptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final SessionHistory conversationHistory;
        
        public ConversationContext(String sessionId, String currentSymptoms, 
                                 ActorRef<TriageResponse> replyTo, SessionHistory history) {
            this.sessionId = sessionId;
            this.currentSymptoms = currentSymptoms;
            this.replyTo = replyTo;
            this.conversationHistory = history;
        }
    }

    // PHASE A1: Conversation-aware LLM request
    public static class ConversationLLMRequest implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final String medicalContext;
        public final SessionHistory conversationHistory;
        
        public ConversationLLMRequest(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo,
                                    String medicalContext, SessionHistory history) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.medicalContext = medicalContext;
            this.conversationHistory = history;
        }
    }

    // PHASE A1: Conversation processing completion message
    public static class ConversationProcessingComplete implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final LLMAnalysisResult llmResult;
        public final SessionHistory conversationHistory;

        public ConversationProcessingComplete(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo, 
                                            LLMAnalysisResult llmResult, SessionHistory conversationHistory) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.llmResult = llmResult;
            this.conversationHistory = conversationHistory;
        }
    }

    // Existing internal messages
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

    // PHASE A1: Conversation-aware retrieval handler
    public static class ConversationRetrievalHandler extends AbstractBehavior<Retrieved> {
        private final String sessionId;
        private final String enhancedQuery;
        private final ActorRef<TriageResponse> originalReplyTo;
        private final ActorRef<TriageCommand> triageRouter;
        private final SessionHistory conversationHistory;

        public static Behavior<Retrieved> create(String sessionId, String enhancedQuery, 
                                               ActorRef<TriageResponse> originalReplyTo,
                                               ActorRef<TriageCommand> triageRouter,
                                               SessionHistory conversationHistory) {
            return Behaviors.setup(context -> new ConversationRetrievalHandler(context, sessionId, 
                enhancedQuery, originalReplyTo, triageRouter, conversationHistory));
        }

        private ConversationRetrievalHandler(ActorContext<Retrieved> context, String sessionId, String enhancedQuery,
                                           ActorRef<TriageResponse> originalReplyTo, ActorRef<TriageCommand> triageRouter,
                                           SessionHistory conversationHistory) {
            super(context);
            this.sessionId = sessionId;
            this.enhancedQuery = enhancedQuery;
            this.originalReplyTo = originalReplyTo;
            this.triageRouter = triageRouter;
            this.conversationHistory = conversationHistory;
        }

        @Override
        public Receive<Retrieved> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Retrieved.class, this::onRetrieved)
                    .build();
        }

        private Behavior<Retrieved> onRetrieved(Retrieved result) {
            if (result.success) {
                String medicalContext = buildConversationContext(result.chunks);
                triageRouter.tell(new ConversationLLMRequest(sessionId, enhancedQuery, originalReplyTo, 
                    medicalContext, conversationHistory));
            } else {
                triageRouter.tell(new ProcessingError(sessionId, enhancedQuery, originalReplyTo, 
                    "Conversation-aware vector retrieval failed"));
            }
            return Behaviors.stopped();
        }
        
        private String buildConversationContext(List<RetrievedChunk> chunks) {
            if (chunks.isEmpty()) {
                return "Limited medical context available for conversation assessment.";
            }
            
            StringBuilder context = new StringBuilder();
            context.append("MEDICAL EVIDENCE BASE:\n\n");
            
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk chunk = chunks.get(i);
                context.append(String.format("[%d] %s (Source: %s, Relevance: %.3f)\n%s\n\n",
                    i + 1, chunk.category.toUpperCase(), chunk.sourceName, chunk.score, chunk.text));
            }
            
            return context.toString();
        }
    }

    // PHASE A1: Conversation-aware LLM response handler
    public static class ConversationLLMHandler extends AbstractBehavior<LLMAnalysisResult> {
        private final String sessionId;
        private final String symptoms;
        private final ActorRef<TriageResponse> originalReplyTo;
        private final ActorRef<TriageCommand> triageRouter;
        private final SessionHistory conversationHistory;

        public static Behavior<LLMAnalysisResult> create(String sessionId, String symptoms,
                                                       ActorRef<TriageResponse> originalReplyTo,
                                                       ActorRef<TriageCommand> triageRouter,
                                                       SessionHistory conversationHistory) {
            return Behaviors.setup(context -> new ConversationLLMHandler(context, sessionId, 
                symptoms, originalReplyTo, triageRouter, conversationHistory));
        }

        private ConversationLLMHandler(ActorContext<LLMAnalysisResult> context, String sessionId, String symptoms,
                                     ActorRef<TriageResponse> originalReplyTo, ActorRef<TriageCommand> triageRouter,
                                     SessionHistory conversationHistory) {
            super(context);
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.originalReplyTo = originalReplyTo;
            this.triageRouter = triageRouter;
            this.conversationHistory = conversationHistory;
        }

        @Override
        public Receive<LLMAnalysisResult> createReceive() {
            return newReceiveBuilder()
                    .onMessage(LLMAnalysisResult.class, this::onLLMResult)
                    .build();
        }

        private Behavior<LLMAnalysisResult> onLLMResult(LLMAnalysisResult result) {
            triageRouter.tell(new ConversationProcessingComplete(sessionId, symptoms, originalReplyTo, 
                result, conversationHistory));
            return Behaviors.stopped();
        }
    }

    // Existing vector retrieval handler (kept for compatibility)
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

    // PHASE A1: Updated create method with sessionActor parameter
    public static Behavior<TriageCommand> create(
            ActorRef<LLMCommand> llmActor,
            ActorRef<RetrievalCommand> retrievalActor,
            ActorRef<LogCommand> logger,
            ActorRef<CareCommand> emergencyCare,
            ActorRef<CareCommand> selfCare,
            ActorRef<CareCommand> appointmentCare,
            ActorRef<SessionCommand> sessionActor) {  // PHASE A1: Added sessionActor
        return Behaviors.setup(context -> 
            new TriageRouterActor(context, llmActor, retrievalActor, logger, 
                                emergencyCare, selfCare, appointmentCare, sessionActor));
    }

    // PHASE A1: Updated constructor with sessionActor
    private TriageRouterActor(ActorContext<TriageCommand> context,
                             ActorRef<LLMCommand> llmActor,
                             ActorRef<RetrievalCommand> retrievalActor,
                             ActorRef<LogCommand> logger,
                             ActorRef<CareCommand> emergencyCare,
                             ActorRef<CareCommand> selfCare,
                             ActorRef<CareCommand> appointmentActor,
                             ActorRef<SessionCommand> sessionActor) {  // PHASE A1: Added parameter
        super(context);
        this.llmActor = llmActor;
        this.retrievalActor = retrievalActor;
        this.logger = logger;
        this.emergencyCare = emergencyCare;
        this.selfCare = selfCare;
        this.appointmentCare = appointmentActor;
        this.sessionActor = sessionActor;  // PHASE A1: Initialize session actor
        
        getContext().getLog().info("🔀 TriageRouterActor initialized with conversation memory support");
    }

    @Override
    public Receive<TriageCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessSymptoms.class, this::onProcessSymptoms)
                .onMessage(ConversationContext.class, this::onConversationContext)  // PHASE A1: Added
                .onMessage(ConversationLLMRequest.class, this::onConversationLLMRequest)  // PHASE A1: Added
                .onMessage(ConversationProcessingComplete.class, this::onConversationProcessingComplete)  // PHASE A1: Added
                .onMessage(VectorRetrievalComplete.class, this::onVectorRetrievalComplete)
                .onMessage(ProcessingError.class, this::onProcessingError)
                .onMessage(ProcessingComplete.class, this::onProcessingComplete)
                .build();
    }

    /**
     * PHASE A1: Enhanced onProcessSymptoms with conversation memory
     */
    private Behavior<TriageCommand> onProcessSymptoms(ProcessSymptoms msg) {
        getContext().getLog().info("🔀 Processing symptoms with conversation context for session [{}]: {}", 
            msg.sessionId, msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Starting conversation-aware triage process", "INFO"));

        // PHASE A1: Get conversation history first
        CompletionStage<SessionHistory> historyFuture = AskPattern.ask(
            sessionActor,
            replyTo -> new GetSessionHistory(msg.sessionId, replyTo),
            Duration.ofSeconds(5),
            getContext().getSystem().scheduler()
        );
        
        // Process with conversation context
        getContext().pipeToSelf(historyFuture, (history, failure) -> {
            if (failure != null) {
                logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
                    "No conversation history found - treating as new conversation", "INFO"));
                return new ConversationContext(msg.sessionId, msg.symptoms, msg.replyTo, 
                    new SessionHistory(msg.sessionId, List.of()));
            } else {
                logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
                    "Conversation history retrieved: " + history.totalInteractions + " previous interactions", "INFO"));
                return new ConversationContext(msg.sessionId, msg.symptoms, msg.replyTo, history);
            }
        });
        
        return this;
    }

    /**
     * PHASE A1: Handle conversation-aware processing
     */
    private Behavior<TriageCommand> onConversationContext(ConversationContext msg) {
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Processing with conversation history: " + msg.conversationHistory.totalInteractions + " previous messages", "INFO"));
        
        // PHASE A1: Build enhanced query including conversation context
        String enhancedQuery = buildConversationAwareQuery(msg.currentSymptoms, msg.conversationHistory);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Enhanced query with conversation context", "DEBUG"));
        
        // PHASE A1: Vector retrieval with conversation context
        ActorRef<Retrieved> retrievalHandler = getContext().spawn(
            ConversationRetrievalHandler.create(msg.sessionId, enhancedQuery, msg.replyTo, 
                getContext().getSelf(), msg.conversationHistory),
            "conv-retrieval-" + msg.sessionId
        );

        retrievalActor.tell(new Retrieve(msg.sessionId, enhancedQuery, 5, retrievalHandler));

        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Vector search initiated with conversation awareness", "DEBUG"));

        return this;
    }

    /**
     * PHASE A1: Handle conversation-aware LLM requests
     */
    private Behavior<TriageCommand> onConversationLLMRequest(ConversationLLMRequest msg) {
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Preparing conversation-aware LLM analysis", "INFO"));
        
        // Build conversation context for LLM
        String conversationContext = buildConversationContextForLLM(msg.conversationHistory);
        
        // Create enhanced context including conversation history + medical evidence
        String fullContext = conversationContext + "\n\n" + msg.medicalContext;
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Sending conversation-aware analysis to LLM", "INFO"));
        
        // Send to LLM with conversation context
        ActorRef<LLMAnalysisResult> llmHandler = getContext().spawn(
            ConversationLLMHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, 
                getContext().getSelf(), msg.conversationHistory),
            "conv-llm-handler-" + msg.sessionId
        );

        llmActor.tell(new AnalyzeSymptoms(msg.sessionId, msg.symptoms, fullContext, llmHandler));

        return this;
    }

    /**
     * PHASE A1: Handle conversation processing completion
     */
    private Behavior<TriageCommand> onConversationProcessingComplete(ConversationProcessingComplete msg) {
        if (!msg.llmResult.success) {
            logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
                "Conversation-aware LLM analysis failed: " + msg.llmResult.errorMessage, "ERROR"));
            
            msg.replyTo.tell(new TriageResponse(msg.sessionId, msg.symptoms, 
                "error", "Conversation analysis failed", "unknown", false));
            return this;
        }

        // PHASE A1: Classification with conversation awareness
        String classification = classifyUrgencyWithConversation(msg.llmResult, msg.conversationHistory);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Conversation-aware classification: " + classification, "INFO"));

        // Route to care actor
        routeToSpecializedCare(msg.sessionId, msg.symptoms, msg.replyTo, msg.llmResult, classification);
        
        // PHASE A1: Update conversation session
        updateConversationSession(msg.sessionId, msg.symptoms, msg.llmResult.analysis);
        
        return this;
    }

    /**
     * PHASE A1: Build conversation-aware query
     */
    private String buildConversationAwareQuery(String currentSymptoms, SessionHistory history) {
        if (history.interactions.isEmpty()) {
            logger.tell(new LogEvent(history.sessionId, "TriageRouter", 
                "New conversation - no previous context", "DEBUG"));
            return currentSymptoms;
        }
        
        StringBuilder enhancedQuery = new StringBuilder();
        
        // Add recent conversation context (last 4 interactions = 2 exchanges)
        List<String> recentMessages = history.interactions.size() > 4 ?
            history.interactions.subList(history.interactions.size() - 4, history.interactions.size()) :
            history.interactions;
        
        if (!recentMessages.isEmpty()) {
            enhancedQuery.append("CONVERSATION CONTEXT: ");
            
            // Extract user messages only (every other interaction)
            for (int i = 0; i < recentMessages.size(); i += 2) {
                if (i < recentMessages.size()) {
                    enhancedQuery.append("Previous: ").append(recentMessages.get(i)).append("; ");
                }
            }
            
            enhancedQuery.append("CURRENT: ");
        }
        
        enhancedQuery.append(currentSymptoms);
        
        System.out.println("💬 PHASE A1: Enhanced query with conversation context");
        System.out.println("💬 Original: " + currentSymptoms);
        System.out.println("💬 Enhanced: " + enhancedQuery.toString());
        
        return enhancedQuery.toString();
    }

    /**
     * PHASE A1: Build conversation context for LLM
     */
    private String buildConversationContextForLLM(SessionHistory history) {
        if (history.interactions.isEmpty()) {
            return "CONVERSATION STATUS: New medical consultation - no previous history.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("MEDICAL CONVERSATION HISTORY:\n");
        
        // Get last 4 interactions (2 user-bot exchanges)
        List<String> recentInteractions = history.interactions.size() > 4 ?
            history.interactions.subList(history.interactions.size() - 4, history.interactions.size()) :
            history.interactions;
        
        for (int i = 0; i < recentInteractions.size(); i += 2) {
            if (i + 1 < recentInteractions.size()) {
                context.append("Patient Previously: ").append(recentInteractions.get(i)).append("\n");
                
                String prevResponse = recentInteractions.get(i + 1);
                String summary = prevResponse.length() > 150 ? 
                    prevResponse.substring(0, 150) + "..." : prevResponse;
                context.append("Previous Assessment: ").append(summary).append("\n\n");
            }
        }
        
        context.append("CONVERSATION ANALYSIS TASK:\n");
        context.append("Consider symptom progression, new developments, and relationship to previous concerns.\n");
        
        return context.toString();
    }

    /**
     * PHASE A1: Classification with conversation awareness
     */
    private String classifyUrgencyWithConversation(LLMAnalysisResult llmResult, SessionHistory history) {
        String analysis = llmResult.analysis.toLowerCase();
        String severity = llmResult.severity.toLowerCase();
        
        // Check for symptom progression indicators
        boolean symptomsProgressing = checkSymptomProgression(history);
        boolean previousEmergencyFlag = checkPreviousEmergencyFlags(history);
        
        // Enhanced emergency classification
        if (severity.contains("high") || severity.contains("critical") ||
            analysis.contains("emergency") || analysis.contains("call 911") ||
            analysis.contains("immediate") || previousEmergencyFlag ||
            (symptomsProgressing && analysis.contains("concerning"))) {
            
            logger.tell(new LogEvent(history.sessionId, "TriageRouter", 
                "Emergency classification - progression: " + symptomsProgressing + 
                ", previous flags: " + previousEmergencyFlag, "INFO"));
            return "emergency";
        }
        
        // Self-care classification
        if (severity.contains("low") || severity.contains("mild") ||
            analysis.contains("home care") || analysis.contains("self-treat")) {
            return "self-care";
        }
        
        return "appointment";
    }

    /**
     * PHASE A1: Update conversation session
     */
    private void updateConversationSession(String sessionId, String userInput, String systemResponse) {
        sessionActor.tell(new UpdateSession(sessionId, userInput, systemResponse));
        logger.tell(new LogEvent(sessionId, "TriageRouter", 
            "Conversation session updated", "DEBUG"));
    }

    /**
     * PHASE A1: Check for symptom progression
     */
    private boolean checkSymptomProgression(SessionHistory history) {
        if (history.interactions.size() < 2) return false;
        
        String recentConversation = String.join(" ", history.interactions).toLowerCase();
        
        return recentConversation.contains("getting worse") || 
               recentConversation.contains("more severe") ||
               recentConversation.contains("spreading") ||
               recentConversation.contains("radiating") ||
               recentConversation.contains("now also");
    }

    /**
     * PHASE A1: Check for previous emergency flags
     */
    private boolean checkPreviousEmergencyFlags(SessionHistory history) {
        if (history.interactions.isEmpty()) return false;
        
        String conversationText = String.join(" ", history.interactions).toLowerCase();
        
        return conversationText.contains("emergency") ||
               conversationText.contains("call 911") ||
               conversationText.contains("immediate care");
    }

    // Keep existing methods for compatibility
    private Behavior<TriageCommand> onVectorRetrievalComplete(VectorRetrievalComplete msg) {
        String enrichedContext = buildEnrichedContext(msg.retrievalResult.chunks, msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Vector retrieval completed, asking LLM with enriched context", "DEBUG"));
        
        ActorRef<LLMAnalysisResult> llmHandler = getContext().spawn(
            LLMResponseHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, getContext().getSelf()),
            "llm-handler-" + msg.sessionId
        );

        llmActor.tell(new AnalyzeSymptoms(msg.sessionId, msg.symptoms, enrichedContext, llmHandler));

        return this;
    }

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
        
        String chunkInfo = chunks.stream()
            .map(c -> String.format("%s(%.3f)", c.id, c.score))
            .collect(Collectors.joining(", "));
        
        logger.tell(new LogEvent(sessionId, "TriageRouter", 
            "Using medical knowledge: " + chunkInfo, "INFO"));
        
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
        HandleTriageCase careMsg = new HandleTriageCase(
            sessionId, symptoms, llmResult.analysis, llmResult.severity, replyTo
        );

        ActorRef<CareCommand> targetActor;
        String careType;
        
        switch (classification.toLowerCase()) {
            case "non-medical":
                logger.tell(new LogEvent(sessionId, "TriageRouter", 
                    "Non-medical input detected", "INFO"));
                
                TriageResponse nonMedicalResponse = new TriageResponse(
                    sessionId, symptoms, "NON-MEDICAL",
                    "⚠️ I'm a medical triage assistant designed to help with health symptoms and concerns.",
                    "NON_MEDICAL", true
                );
                
                replyTo.tell(nonMedicalResponse);
                return;
                
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

        targetActor.tell(careMsg);
    }

    private String classifyUrgency(LLMAnalysisResult llmResult) {
        String analysis = llmResult.analysis.toLowerCase();
        String severity = llmResult.severity.toLowerCase();
        String recommendation = llmResult.recommendation.toLowerCase();
        
        if (severity.contains("non_medical") || severity.equals("non_medical")) {
            return "non-medical";
        }
        
        if (severity.contains("high") || severity.contains("critical") ||
            analysis.contains("call 911") || analysis.contains("emergency services") ||
            analysis.contains("immediate") || analysis.contains("urgent") ||
            recommendation.contains("call 911") || recommendation.contains("emergency room")) {
            return "emergency";
        }
        
        if (severity.contains("low") || severity.contains("mild") ||
            analysis.contains("home care") || analysis.contains("rest") ||
            analysis.contains("over-the-counter") || analysis.contains("self-treat")) {
            return "self-care";
        }
        
        return "appointment";
    }
}