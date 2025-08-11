package com.triage;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.cluster.typed.*;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Subscribe;
import com.typesafe.config.*;
import java.util.Scanner;

/**
 * Cluster-enabled Medical Triage Assistant
 * Supports multi-node deployment while preserving all existing functionality
 * 
 * Usage:
 * - mvn exec:java -Dexec.mainClass="com.triage.ClusterMain" -Dexec.args="2551" (Primary node)
 * - mvn exec:java -Dexec.mainClass="com.triage.ClusterMain" -Dexec.args="2552" (Service node)
 */
public class ClusterMain {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("🏥 Starting Medical Triage Cluster - Primary Node (2551)");
            startNode(2551, true);  // Primary node with web interface
        } else {
            int port = Integer.parseInt(args[0]);
            boolean isPrimary = port == 2551;
            System.out.println("🏥 Starting Medical Triage Cluster - Node on port " + port);
            startNode(port, isPrimary);
        }
    }
    
    private static void startNode(int port, boolean isPrimary) {
        // Override port in configuration
        Config portConfig = ConfigFactory.parseString(
            "akka.remote.artery.canonical.port=" + port);
        Config config = portConfig.withFallback(ConfigFactory.load());
        
        System.out.println("🔧 Initializing cluster node on port " + port + "...");
        
        // Create actor system with cluster configuration
        ActorSystem<Void> system = ActorSystem.create(
            createClusterNodeBehavior(isPrimary),  // Create cluster-specific behavior
            "MedicalTriageSystem",
            config
        );
        
        // Get cluster instance
        Cluster cluster = Cluster.get(system);
        
        // Display cluster information
        System.out.println("🏥 ============================================");
        System.out.println("🏥 MEDICAL TRIAGE ASSISTANT - CLUSTER NODE");
        System.out.println("🏥 ============================================");
        System.out.println("🔗 Node Address: " + cluster.selfMember().address());
        System.out.println("🎭 Node Roles: " + cluster.selfMember().roles());
        System.out.println("⚡ Cluster Status: Initializing...");
        
        if (isPrimary) {
            System.out.println("🌐 Web Interface: http://localhost:8080 (Primary Node Only)");
            System.out.println("💻 Console Interface: Available on this node");
        } else {
            System.out.println("⚙️  Service Node: Processing medical queries");
            System.out.println("💻 Console Interface: Not available on service nodes");
        }
        
        System.out.println("🏥 ============================================");
        
        // Wait for cluster to form
        try {
            Thread.sleep(3000);
            System.out.println("✅ Cluster node ready for medical consultations");
            
            // Only start interactive console on primary node
            if (isPrimary) {
                startInteractiveConsole(system);
            } else {
                System.out.println("⚙️ Service node running... (Ctrl+C to stop)");
                // Keep service node alive
                system.getWhenTerminated().toCompletableFuture().join();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Create cluster-specific behavior that spawns cluster listener
     */
    private static Behavior<Void> createClusterNodeBehavior(boolean isPrimary) {
        return Behaviors.setup(context -> {
            // Spawn cluster listener within the guardian actor
            ActorRef<ClusterEvent.ClusterDomainEvent> clusterListener = context.spawn(
                ClusterListener.create(),
                "cluster-listener"
            );
            
            // Use Main's cluster-enabled behavior for the rest
            return Main.createClusterEnabledBehavior(isPrimary);
        });
    }
    
    /**
     * Interactive console for primary node only
     */
    private static void startInteractiveConsole(ActorSystem<Void> system) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n💬 Interactive Console Ready (Primary Node)");
        System.out.println("💬 Commands: 'quit' to exit, 'cluster' for status, 'help' for guidance\n");
        
        while (true) {
            System.out.print("🩺 Cluster Console: ");
            String userInput = scanner.nextLine().trim();
            
            if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n👋 Shutting down cluster node...");
                break;
            }
            
            if (userInput.equalsIgnoreCase("cluster")) {
                Cluster cluster = Cluster.get(system);
                System.out.println("\n🔗 CLUSTER STATUS:");
                System.out.println("   Address: " + cluster.selfMember().address());
                System.out.println("   Members: " + cluster.state().members());
                System.out.println("   Leader: " + cluster.state().leader());
                continue;
            }
            
            if (userInput.equalsIgnoreCase("help")) {
                System.out.println("\n📋 CLUSTER COMMANDS:");
                System.out.println("   • 'cluster' - Show cluster status");
                System.out.println("   • 'quit' - Shutdown this node");
                System.out.println("   • Medical queries - Process through cluster");
                System.out.println("   • Web interface: http://localhost:8080");
                continue;
            }
            
            if (!userInput.isEmpty()) {
                System.out.println("💡 Processing: " + userInput);
                System.out.println("🌐 For full functionality, use web interface: http://localhost:8080");
            }
        }
        
        scanner.close();
        system.terminate();
    }
}

/**
 * Cluster event listener for monitoring cluster membership
 */
class ClusterListener extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {
    
    public static Behavior<ClusterEvent.ClusterDomainEvent> create() {
        return Behaviors.setup(ClusterListener::new);
    }
    
    private ClusterListener(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
        super(context);
        
        // Subscribe to cluster events
        Cluster cluster = Cluster.get(context.getSystem());
        cluster.subscriptions().tell(Subscribe.create(
            context.getSelf(), 
            ClusterEvent.ClusterDomainEvent.class
        ));
        
        context.getLog().info("🔍 ClusterListener initialized - monitoring cluster events");
    }
    
    @Override
    public Receive<ClusterEvent.ClusterDomainEvent> createReceive() {
        return newReceiveBuilder()
            .onMessage(ClusterEvent.MemberUp.class, this::onMemberUp)
            .onMessage(ClusterEvent.MemberRemoved.class, this::onMemberRemoved)
            .onMessage(ClusterEvent.UnreachableMember.class, this::onUnreachableMember)
            .onMessage(ClusterEvent.MemberJoined.class, this::onMemberJoined)
            .build();
    }
    
    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberUp(ClusterEvent.MemberUp event) {
        System.out.println("🟢 CLUSTER MEMBER UP: " + event.member().address());
        getContext().getLog().info("Cluster member up: {}", event.member().address());
        return this;
    }
    
    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberJoined(ClusterEvent.MemberJoined event) {
        System.out.println("🔵 CLUSTER MEMBER JOINED: " + event.member().address());
        getContext().getLog().info("Cluster member joined: {}", event.member().address());
        return this;
    }
    
    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberRemoved(ClusterEvent.MemberRemoved event) {
        System.out.println("🔴 CLUSTER MEMBER REMOVED: " + event.member().address());
        getContext().getLog().info("Cluster member removed: {}", event.member().address());
        return this;
    }
    
    private Behavior<ClusterEvent.ClusterDomainEvent> onUnreachableMember(ClusterEvent.UnreachableMember event) {
        System.out.println("⚠️  CLUSTER MEMBER UNREACHABLE: " + event.member().address());
        getContext().getLog().warn("Cluster member unreachable: {}", event.member().address());
        return this;
    }
}