package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * UserInputActor - Entry point for all user interactions
 * Demonstrates TELL communication pattern
 * Responsibility: Accept user input and forward to triage system
 */
public class UserInputActor extends AbstractBehavior<UserInputCommand> {

    private final ActorRef<TriageCommand> triageRouter;
    private final ActorRef<LogCommand> logger;
    private final ActorRef<SessionCommand> sessionActor;

    public static Behavior<UserInputCommand> create(
            ActorRef<TriageCommand> triageRouter,
            ActorRef<LogCommand> logger,
            ActorRef<SessionCommand> sessionActor) {
        return Behaviors.setup(context -> 
            new UserInputActor(context, triageRouter, logger, sessionActor));
    }

    private UserInputActor(ActorContext<UserInputCommand> context,
                          ActorRef<TriageCommand> triageRouter,
                          ActorRef<LogCommand> logger,
                          ActorRef<SessionCommand> sessionActor) {
        super(context);
        this.triageRouter = triageRouter;
        this.logger = logger;
        this.sessionActor = sessionActor;
        
        getContext().getLog().info("👤 UserInputActor initialized");
    }

    @Override
    public Receive<UserInputCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(UserSymptomInput.class, this::onUserSymptomInput)
                .build();
    }

    private Behavior<UserInputCommand> onUserSymptomInput(UserSymptomInput msg) {
        getContext().getLog().info("📝 User input received [{}]: {}", msg.sessionId, msg.symptoms);
        
        // Log the interaction
        logger.tell(new LogEvent(msg.sessionId, "UserInputActor", 
            "Received user input: " + msg.symptoms, "INFO"));

        // Create response handler for this session
        ActorRef<TriageResponse> responseHandler = getContext().spawn(
            createResponseHandler(msg.sessionId, msg.symptoms),
            "response-handler-" + msg.sessionId
        );

        // TELL PATTERN: Fire-and-forget message to triage router
        triageRouter.tell(new ProcessSymptoms(msg.sessionId, msg.symptoms, responseHandler));
        
        logger.tell(new LogEvent(msg.sessionId, "UserInputActor", 
            "Sent to TriageRouter via TELL pattern", "DEBUG"));

        return this;
    }

    private Behavior<TriageResponse> createResponseHandler(String sessionId, String originalInput) {
        return Behaviors.setup(context -> 
            Behaviors.receive(TriageResponse.class)
                .onMessage(TriageResponse.class, response -> {
                    context.getLog().info("🎯 FINAL TRIAGE RESULT for session [{}]:", sessionId);
                    context.getLog().info("═══════════════════════════════════════════════");
                    context.getLog().info("📋 Original Symptoms: {}", response.originalSymptoms);
                    context.getLog().info("🏷️  Classification: {}", response.classification);
                    context.getLog().info("⚡ Severity: {}", response.severity);
                    context.getLog().info("💡 Recommendation: {}", response.recommendation);
                    context.getLog().info("✅ Success: {}", response.success);
                    context.getLog().info("═══════════════════════════════════════════════");
                    
                    // Update session with final result
                    sessionActor.tell(new UpdateSession(sessionId, originalInput, response.recommendation));
                    
                    // Log completion
                    logger.tell(new LogEvent(sessionId, "ResponseHandler", 
                        "Triage completed: " + response.classification, "INFO"));
                    
                    return Behaviors.stopped();
                })
                .build()
        );
    }
}