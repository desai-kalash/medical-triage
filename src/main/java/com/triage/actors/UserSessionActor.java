package com.triage.actors;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import com.triage.messages.Messages.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserSessionActor - Session management and history tracking
 * Demonstrates both TELL and ASK patterns
 */
public class UserSessionActor extends AbstractBehavior<SessionCommand> {

    private final Map<String, List<String>> sessionHistory;
    private final ActorRef<LogCommand> logger;
    private static final int MAX_HISTORY_SIZE = 5;

    public static Behavior<SessionCommand> create(ActorRef<LogCommand> logger) {
        return Behaviors.setup(context -> new UserSessionActor(context, logger));
    }

    private UserSessionActor(ActorContext<SessionCommand> context, ActorRef<LogCommand> logger) {
        super(context);
        this.sessionHistory = new ConcurrentHashMap<>();
        this.logger = logger;
        
        getContext().getLog().info("👥 UserSessionActor initialized");
    }

    @Override
    public Receive<SessionCommand> createReceive() {
        return newReceiveBuilder()
                .onMessage(UpdateSession.class, this::onUpdateSession)
                .onMessage(GetSessionHistory.class, this::onGetSessionHistory)
                .build();
    }

    private Behavior<SessionCommand> onUpdateSession(UpdateSession msg) {
        getContext().getLog().debug("👥 Updating session [{}]", msg.sessionId);
        
        // Get or create session history
        List<String> history = sessionHistory.computeIfAbsent(msg.sessionId, 
            k -> Collections.synchronizedList(new ArrayList<>()));
        
        // Add interaction to history
        String interaction = String.format("[%s] Input: %s | Response: %s",
            msg.timestamp.atZone(java.time.ZoneId.systemDefault()).format(
                DateTimeFormatter.ofPattern("HH:mm:ss")),
            msg.userInput,
            msg.systemResponse.length() > 100 ? 
                msg.systemResponse.substring(0, 100) + "..." : msg.systemResponse);
        
        history.add(interaction);
        
        // Limit history size
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
        
        logger.tell(new LogEvent(msg.sessionId, "UserSessionActor", 
            "Session updated, total interactions: " + history.size(), "DEBUG"));
        
        return this;
    }

    private Behavior<SessionCommand> onGetSessionHistory(GetSessionHistory msg) {
        getContext().getLog().debug("👥 Retrieving history for session [{}]", msg.sessionId);
        
        List<String> history = sessionHistory.getOrDefault(msg.sessionId, 
            Collections.emptyList());
        
        // Create a copy to avoid concurrent modification
        List<String> historyCopy = new ArrayList<>(history);
        
        SessionHistory response = new SessionHistory(msg.sessionId, historyCopy);
        msg.replyTo.tell(response);
        
        logger.tell(new LogEvent(msg.sessionId, "UserSessionActor", 
            "History retrieved: " + historyCopy.size() + " interactions", "DEBUG"));
        
        return this;
    }
}