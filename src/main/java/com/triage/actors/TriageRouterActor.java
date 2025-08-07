package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * TriageRouterActor - Core system orchestrator
 * Demonstrates ASK and FORWARD communication patterns using manual message handling
 * Responsibility: Coordinate retrieval → LLM analysis → specialized care routing
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

    // Additional internal messages
    public static class RetrievalComplete implements TriageCommand {
        public final String sessionId;
        public final String symptoms;
        public final ActorRef<TriageResponse> replyTo;
        public final RetrievalResult retrievalResult;

        public RetrievalComplete(String sessionId, String symptoms, ActorRef<TriageResponse> replyTo, RetrievalResult retrievalResult) {
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.replyTo = replyTo;
            this.retrievalResult = retrievalResult;
        }
    }

    // Response handlers for manual ASK pattern
    public static class RetrievalResponseHandler extends AbstractBehavior<RetrievalResult> {
        private final String sessionId;
        private final String symptoms;
        private final ActorRef<TriageResponse> originalReplyTo;
        private final ActorRef<TriageCommand> triageRouter;

        public static Behavior<RetrievalResult> create(String sessionId, String symptoms, 
                                                      ActorRef<TriageResponse> originalReplyTo,
                                                      ActorRef<TriageCommand> triageRouter) {
            return Behaviors.setup(context -> new RetrievalResponseHandler(context, sessionId, symptoms, originalReplyTo, triageRouter));
        }

        private RetrievalResponseHandler(ActorContext<RetrievalResult> context, String sessionId, String symptoms,
                                        ActorRef<TriageResponse> originalReplyTo, ActorRef<TriageCommand> triageRouter) {
            super(context);
            this.sessionId = sessionId;
            this.symptoms = symptoms;
            this.originalReplyTo = originalReplyTo;
            this.triageRouter = triageRouter;
        }

        @Override
        public Receive<RetrievalResult> createReceive() {
            return newReceiveBuilder()
                    .onMessage(RetrievalResult.class, this::onRetrievalResult)
                    .build();
        }

        private Behavior<RetrievalResult> onRetrievalResult(RetrievalResult result) {
            if (result.success) {
                triageRouter.tell(new RetrievalComplete(sessionId, symptoms, originalReplyTo, result));
            } else {
                triageRouter.tell(new ProcessingError(sessionId, symptoms, originalReplyTo, "Retrieval failed"));
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
        
        getContext().getLog().info("🔀 TriageRouterActor initialized");
    }

    @Override
    public Receive<TriageCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(ProcessSymptoms.class, this::onProcessSymptoms)
                .onMessage(RetrievalComplete.class, this::onRetrievalComplete)
                .onMessage(ProcessingError.class, this::onProcessingError)
                .onMessage(ProcessingComplete.class, this::onProcessingComplete)
                .build();
    }

    private Behavior<TriageCommand> onProcessSymptoms(ProcessSymptoms msg) {
        getContext().getLog().info("🔀 Processing symptoms for session [{}]: {}", 
            msg.sessionId, msg.symptoms);
        
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Starting triage process", "INFO"));

        // STEP 1: Manual ASK PATTERN - Request context from retrieval actor
        ActorRef<RetrievalResult> retrievalHandler = getContext().spawn(
            RetrievalResponseHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, getContext().getSelf()),
            "retrieval-handler-" + msg.sessionId
        );

        // Send message to retrieval actor with our response handler
        retrievalActor.tell(new RetrieveContext(msg.sessionId, msg.symptoms, retrievalHandler));

        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Manual ASK pattern initiated with RetrievalActor", "DEBUG"));

        return this;
    }

    private Behavior<TriageCommand> onRetrievalComplete(RetrievalComplete msg) {
        logger.tell(new LogEvent(msg.sessionId, "TriageRouter", 
            "Retrieval completed, asking LLM", "DEBUG"));
        
        // STEP 2: Manual ASK PATTERN - Request LLM analysis with retrieved context
        ActorRef<LLMAnalysisResult> llmHandler = getContext().spawn(
            LLMResponseHandler.create(msg.sessionId, msg.symptoms, msg.replyTo, getContext().getSelf()),
            "llm-handler-" + msg.sessionId
        );

        // Send message to LLM actor with our response handler
        llmActor.tell(new AnalyzeSymptoms(msg.sessionId, msg.symptoms, msg.retrievalResult.context, llmHandler));

        return this;
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
        
        // Check for non-medical input first
        if (severity.contains("non_medical") || severity.equals("non_medical")) {
            return "non-medical";
        }
        
        // Emergency classification
        if (severity.contains("high") || severity.contains("severe") || severity.contains("critical") ||
            analysis.contains("emergency") || analysis.contains("urgent") || 
            analysis.contains("chest pain") || analysis.contains("difficulty breathing") ||
            analysis.contains("severe pain") || analysis.contains("911")) {
            return "emergency";
        }
        
        // Self-care classification
        if (severity.contains("low") || severity.contains("mild") || severity.contains("minor") ||
            analysis.contains("rest") || analysis.contains("home care") || 
            analysis.contains("over-the-counter") || analysis.contains("self-treat")) {
            return "self-care";
        }
        
        // Default to appointment for moderate cases
        return "appointment";
    }
}