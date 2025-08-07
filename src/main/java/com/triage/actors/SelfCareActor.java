package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

/**
 * SelfCareActor - Handles mild conditions suitable for home treatment
 */
public class SelfCareActor extends AbstractBehavior<CareCommand> {

    private final ActorRef<LogCommand> logger;

    public static Behavior<CareCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new SelfCareActor(context, logger));
    }

    private SelfCareActor(ActorContext<CareCommand> context, ActorRef<LogCommand> logger) {
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
        getContext().getLog().info("🏠 SELF-CARE case for session [{}]", msg.sessionId);
        
        logger.tell(new LogEvent(msg.sessionId, "SelfCareActor", 
            "Processing self-care case", "INFO"));

        String selfCareResponse = String.format("""
            💡 SELF-CARE RECOMMENDATIONS
            
            Session: %s
            Symptoms: %s
            Severity: %s
            
            HOME CARE GUIDELINES:
            🌟 Get plenty of rest and sleep
            🌟 Stay well hydrated with water, herbal teas
            🌟 Consider over-the-counter remedies as appropriate
            🌟 Apply heat/cold therapy if applicable
            🌟 Monitor symptoms for changes
            
            Analysis: %s
            
            ⚠️ WHEN TO SEEK MEDICAL CARE:
            • Symptoms worsen or don't improve in 3-5 days
            • New concerning symptoms develop
            • Fever rises above 103°F (39.4°C)
            • You feel unsure about your condition
            
            Contact your healthcare provider if you have concerns.
            """, msg.sessionId, msg.symptoms, msg.severity, msg.analysis);

        logger.tell(new LogEvent(msg.sessionId, "SelfCareActor", 
            "Self-care guidance provided", "INFO"));

        TriageResponse response = new TriageResponse(
            msg.sessionId, 
            msg.symptoms, 
            "SELF-CARE", 
            selfCareResponse, 
            msg.severity, 
            true
        );

        msg.originalSender.tell(response);
        
        return Behaviors.stopped();
    }
}