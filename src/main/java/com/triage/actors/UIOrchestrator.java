package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UIOrchestrator - Handles web interface requests
 * Orchestrates the same flow as console but returns structured web responses
 * Demonstrates complete actor orchestration for web API
 */
public class UIOrchestrator extends AbstractBehavior<UICommand> {

    private final ActorRef<RetrievalCommand> retrievalActor;
    private final ActorRef<LLMCommand> llmActor;
    private final ActorRef<LogCommand> logger;
    private final ActorRef<CareCommand> emergencyCare;
    private final ActorRef<CareCommand> selfCare;
    private final ActorRef<CareCommand> appointmentCare;

    // Internal messages for web flow
    public static class WebRetrievalComplete implements UICommand {
        public final String sessionId;
        public final String originalText;
        public final ActorRef<UIResponse> replyTo;
        public final Retrieved retrievalResult;

        public WebRetrievalComplete(String sessionId, String originalText, ActorRef<UIResponse> replyTo, Retrieved retrievalResult) {
            this.sessionId = sessionId;
            this.originalText = originalText;
            this.replyTo = replyTo;
            this.retrievalResult = retrievalResult;
        }
    }

    public static class WebLLMComplete implements UICommand {
        public final String sessionId;
        public final String originalText;
        public final ActorRef<UIResponse> replyTo;
        public final LLMAnalysisResult llmResult;
        public final List<RetrievedChunk> chunks;

        public WebLLMComplete(String sessionId, String originalText, ActorRef<UIResponse> replyTo, 
                             LLMAnalysisResult llmResult, List<RetrievedChunk> chunks) {
            this.sessionId = sessionId;
            this.originalText = originalText;
            this.replyTo = replyTo;
            this.llmResult = llmResult;
            this.chunks = chunks;
        }
    }

    // Response handlers
    public static class WebRetrievalHandler extends AbstractBehavior<Retrieved> {
        private final String sessionId;
        private final String originalText;
        private final ActorRef<UIResponse> originalReplyTo;
        private final ActorRef<UICommand> uiOrchestrator;

        public static Behavior<Retrieved> create(String sessionId, String originalText,
                                                ActorRef<UIResponse> originalReplyTo,
                                                ActorRef<UICommand> uiOrchestrator) {
            return Behaviors.setup(context -> new WebRetrievalHandler(context, sessionId, originalText, originalReplyTo, uiOrchestrator));
        }

        private WebRetrievalHandler(ActorContext<Retrieved> context, String sessionId, String originalText,
                                   ActorRef<UIResponse> originalReplyTo, ActorRef<UICommand> uiOrchestrator) {
            super(context);
            this.sessionId = sessionId;
            this.originalText = originalText;
            this.originalReplyTo = originalReplyTo;
            this.uiOrchestrator = uiOrchestrator;
        }

        @Override
        public Receive<Retrieved> createReceive() {
            return newReceiveBuilder()
                    .onMessage(Retrieved.class, this::onRetrieved)
                    .build();
        }

        private Behavior<Retrieved> onRetrieved(Retrieved result) {
            uiOrchestrator.tell(new WebRetrievalComplete(sessionId, originalText, originalReplyTo, result));
            return Behaviors.stopped();
        }
    }

    public static class WebLLMHandler extends AbstractBehavior<LLMAnalysisResult> {
        private final String sessionId;
        private final String originalText;
        private final ActorRef<UIResponse> originalReplyTo;
        private final ActorRef<UICommand> uiOrchestrator;
        private final List<RetrievedChunk> chunks;

        public static Behavior<LLMAnalysisResult> create(String sessionId, String originalText,
                                                        ActorRef<UIResponse> originalReplyTo,
                                                        ActorRef<UICommand> uiOrchestrator,
                                                        List<RetrievedChunk> chunks) {
            return Behaviors.setup(context -> new WebLLMHandler(context, sessionId, originalText, originalReplyTo, uiOrchestrator, chunks));
        }

        private WebLLMHandler(ActorContext<LLMAnalysisResult> context, String sessionId, String originalText,
                             ActorRef<UIResponse> originalReplyTo, ActorRef<UICommand> uiOrchestrator,
                             List<RetrievedChunk> chunks) {
            super(context);
            this.sessionId = sessionId;
            this.originalText = originalText;
            this.originalReplyTo = originalReplyTo;
            this.uiOrchestrator = uiOrchestrator;
            this.chunks = chunks;
        }

        @Override
        public Receive<LLMAnalysisResult> createReceive() {
            return newReceiveBuilder()
                    .onMessage(LLMAnalysisResult.class, this::onLLMResult)
                    .build();
        }

        private Behavior<LLMAnalysisResult> onLLMResult(LLMAnalysisResult result) {
            uiOrchestrator.tell(new WebLLMComplete(sessionId, originalText, originalReplyTo, result, chunks));
            return Behaviors.stopped();
        }
    }

    public static Behavior<UICommand> create(
            ActorRef<RetrievalCommand> retrievalActor,
            ActorRef<LLMCommand> llmActor,
            ActorRef<LogCommand> logger,
            ActorRef<CareCommand> emergencyCare,
            ActorRef<CareCommand> selfCare,
            ActorRef<CareCommand> appointmentCare) {
        return Behaviors.setup(context -> 
            new UIOrchestrator(context, retrievalActor, llmActor, logger, emergencyCare, selfCare, appointmentCare));
    }

    private UIOrchestrator(ActorContext<UICommand> context,
                          ActorRef<RetrievalCommand> retrievalActor,
                          ActorRef<LLMCommand> llmActor,
                          ActorRef<LogCommand> logger,
                          ActorRef<CareCommand> emergencyCare,
                          ActorRef<CareCommand> selfCare,
                          ActorRef<CareCommand> appointmentCare) {
        super(context);
        this.retrievalActor = retrievalActor;
        this.llmActor = llmActor;
        this.logger = logger;
        this.emergencyCare = emergencyCare;
        this.selfCare = selfCare;
        this.appointmentCare = appointmentCare;
        
        getContext().getLog().info("🌐 UIOrchestrator initialized for web interface");
    }

    @Override
    public Receive<UICommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(UIQuery.class, this::onUIQuery)
                .onMessage(WebRetrievalComplete.class, this::onWebRetrievalComplete)
                .onMessage(WebLLMComplete.class, this::onWebLLMComplete)
                .build();
    }

    private Behavior<UICommand> onUIQuery(UIQuery msg) {
        getContext().getLog().info("🌐 Web UI query for session [{}]: {}", 
            msg.sessionId, msg.text);
        
        logger.tell(new LogEvent(msg.sessionId, "UIOrchestrator", 
            "Processing web UI query", "INFO"));

        // Step 1: Vector retrieval for medical knowledge
        ActorRef<Retrieved> retrievalHandler = getContext().spawn(
            WebRetrievalHandler.create(msg.sessionId, msg.text, msg.replyTo, getContext().getSelf()),
            "web-retrieval-" + msg.sessionId
        );

        retrievalActor.tell(new Retrieve(msg.sessionId, msg.text, 5, retrievalHandler));

        return this;
    }

    private Behavior<UICommand> onWebRetrievalComplete(WebRetrievalComplete msg) {
        logger.tell(new LogEvent(msg.sessionId, "UIOrchestrator", 
            "Vector retrieval completed, asking LLM", "DEBUG"));

        // Build enriched context
        String enrichedContext = buildEnrichedContext(msg.retrievalResult.chunks, msg.sessionId);

        // Step 2: LLM analysis with medical context
        ActorRef<LLMAnalysisResult> llmHandler = getContext().spawn(
            WebLLMHandler.create(msg.sessionId, msg.originalText, msg.replyTo, getContext().getSelf(), 
                               msg.retrievalResult.chunks),
            "web-llm-" + msg.sessionId
        );

        llmActor.tell(new AnalyzeSymptoms(msg.sessionId, msg.originalText, enrichedContext, llmHandler));

        return this;
    }

    private Behavior<UICommand> onWebLLMComplete(WebLLMComplete msg) {
        if (!msg.llmResult.success) {
            logger.tell(new LogEvent(msg.sessionId, "UIOrchestrator", 
                "LLM analysis failed: " + msg.llmResult.errorMessage, "ERROR"));

            UIResponse errorResponse = new UIResponse(
                msg.sessionId,
                "I'm experiencing technical difficulties. Please try again or contact emergency services if urgent.",
                "Error",
                false,
                List.of(),
                "This is an educational system. Always consult real medical professionals for health concerns."
            );
            
            msg.replyTo.tell(errorResponse);
            return this;
        }

        // Step 3: Build final UI response
        String classification = classifyForUI(msg.llmResult);
        boolean isEmergency = classification.equals("Emergency");
        
        // Convert chunks to sources
        List<UIResponse.Source> sources = msg.chunks.stream()
            .map(c -> new UIResponse.Source(c.sourceName, c.sourceUrl, c.score))
            .collect(Collectors.toList());

        String disclaimer = "This is an educational AI system. " +
                          (isEmergency ? "Call emergency services immediately for urgent situations. " : "") +
                          "Always consult real medical professionals for health concerns.";

        UIResponse response = new UIResponse(
            msg.sessionId,
            msg.llmResult.recommendation,
            classification,
            isEmergency,
            sources,
            disclaimer
        );

        logger.tell(new LogEvent(msg.sessionId, "UIOrchestrator", 
            "Web UI response prepared: " + classification, "INFO"));

        msg.replyTo.tell(response);
        return this;
    }

    private String buildEnrichedContext(List<RetrievedChunk> chunks, String sessionId) {
        if (chunks.isEmpty()) {
            return "General medical knowledge - no specific guidance found for these symptoms.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("MEDICAL KNOWLEDGE BASE CONTEXT:\n\n");
        
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            context.append(String.format("[%d] %s (Source: %s, Relevance: %.3f)\n%s\n\n",
                i + 1, chunk.category.toUpperCase(), chunk.sourceName, chunk.score, chunk.text));
        }
        
        return context.toString();
    }

    private String classifyForUI(LLMAnalysisResult llmResult) {
        String analysis = llmResult.analysis.toLowerCase();
        String severity = llmResult.severity.toLowerCase();
        
        // Check for non-medical input
        if (severity.contains("non_medical") || severity.equals("non_medical")) {
            return "NonMedical";
        }
        
        // Emergency classification
        if (severity.contains("high") || severity.contains("severe") || severity.contains("critical") ||
            analysis.contains("emergency") || analysis.contains("urgent") || 
            analysis.contains("911") || analysis.contains("call emergency")) {
            return "Emergency";
        }
        
        // Self-care classification  
        if (severity.contains("low") || severity.contains("mild") || severity.contains("minor") ||
            analysis.contains("rest") || analysis.contains("home care") || 
            analysis.contains("self-treat")) {
            return "SelfCare";
        }
        
        // Default to appointment
        return "Appointment";
    }
}