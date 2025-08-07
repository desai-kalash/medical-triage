package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * EmergencyCareActor - Handles urgent medical situations
 * Demonstrates FORWARD pattern - receives messages forwarded from TriageRouter
 */
public class EmergencyCareActor extends AbstractBehavior<CareCommand> {

    private final ActorRef<LogCommand> logger;

    public static Behavior<CareCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new EmergencyCareActor(context, logger));
    }

    private EmergencyCareActor(ActorContext<CareCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.logger = logger;
    }

    @Override
    public Receive<CareCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(HandleTriageCase.class, this::onHandleTriageCase)
                .build();
    }

    private Behavior<CareCommand> onHandleTriageCase(HandleTriageCase msg) {
        getContext().getLog().info("🚨 EMERGENCY CASE for session [{}]", msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "EmergencyCareActor", 
            "Processing emergency case", "CRITICAL"));

        String emergencyResponse = String.format("""
            🚨 EMERGENCY MEDICAL ATTENTION REQUIRED
            
            Session: %s
            Symptoms: %s
            Severity: %s
            
            IMMEDIATE ACTIONS:
            ⚡ Call 911 or go to nearest Emergency Room immediately
            ⚡ Do not drive yourself - call ambulance if needed
            ⚡ If chest pain: Take aspirin if not allergic and call 911
            ⚡ If difficulty breathing: Sit upright and seek immediate help
            
            Analysis: %s
            
            ⚠️ This is a medical emergency - do not delay seeking care!
            """, msg.sessionId, msg.symptoms, msg.severity, msg.analysis);

        logger.tell(new LogEvent(msg.sessionId, "EmergencyCareActor", 
            "Emergency response generated", "INFO"));

        // Send response back to original requester (demonstrates preserved sender context from FORWARD)
        TriageResponse response = new TriageResponse(
            msg.sessionId, 
            msg.symptoms, 
            "EMERGENCY", 
            emergencyResponse, 
            msg.severity, 
            true
        );

        msg.originalSender.tell(response);
        
        return Behaviors.stopped(); // Emergency cases terminate after handling
    }
}