/**
 * Medical Triage Assistant - Web Frontend
 * Interactive chat interface with emergency detection and source attribution
 */

// DOM elements
const chat = document.getElementById('chat');
const input = document.getElementById('input');
const sendButton = document.getElementById('send');
const sendText = document.getElementById('send-text');
const sendLoading = document.getElementById('send-loading');
const emergencyBanner = document.getElementById('emergency');
const app = document.getElementById('app');

// Session management
let sessionId = localStorage.getItem('triage_session_id') || null;

/**
 * Add a chat bubble to the conversation
 */
function addBubble(text, sender, isThinking = false) {
    const bubble = document.createElement('div');
    bubble.className = `bubble ${sender}`;
    
    if (isThinking) {
        bubble.className += ' thinking';
        bubble.innerHTML = text;
    } else {
        bubble.innerHTML = formatMessage(text);
    }
    
    chat.appendChild(bubble);
    scrollToBottom();
    
    return bubble;
}

/**
 * Format message text with basic HTML support
 */
function formatMessage(text) {
    return text
        .replace(/\n/g, '<br>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/⚡/g, '<span style="color: #dc2626;">⚡</span>')
        .replace(/🚨/g, '<span style="color: #dc2626;">🚨</span>');
}

/**
 * Add source attribution below the last message
 */
function addSources(sources) {
    if (!sources || sources.length === 0) return;
    
    const sourcesDiv = document.createElement('div');
    sourcesDiv.className = 'sources';
    
    const sourceLinks = sources.map(source => {
        const score = source.score ? ` (${(source.score * 100).toFixed(1)}% match)` : '';
        return `<a href="${source.url}" target="_blank" rel="noopener">${source.name}${score}</a>`;
    }).join(' • ');
    
    sourcesDiv.innerHTML = `<strong>📚 Medical Sources:</strong> ${sourceLinks}`;
    chat.appendChild(sourcesDiv);
    scrollToBottom();
}

/**
 * Scroll chat to bottom
 */
function scrollToBottom() {
    chat.scrollTop = chat.scrollHeight;
}

/**
 * Set loading state
 */
function setLoading(loading) {
    sendButton.disabled = loading;
    sendText.classList.toggle('hidden', loading);
    sendLoading.classList.toggle('hidden', !loading);
    
    if (loading) {
        input.disabled = true;
        sendButton.style.background = '#9ca3af';
    } else {
        input.disabled = false;
        sendButton.style.background = '';
    }
}

/**
 * Show/hide emergency banner
 */
function setEmergencyMode(emergency) {
    emergencyBanner.classList.toggle('hidden', !emergency);
    app.classList.toggle('emergency', emergency);
    
    if (emergency) {
        // Scroll to top to ensure banner is visible
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}

/**
 * Send message to medical triage system
 */
async function sendMessage() {
    const text = input.value.trim();
    if (!text) return;
    
    // Add user message
    addBubble(text, 'me');
    input.value = '';
    
    // Show thinking indicator
    const thinkingBubble = addBubble('🤔 Analyzing your symptoms...', 'bot', true);
    setLoading(true);
    
    try {
        const response = await fetch('/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                text: text,
                sessionId: sessionId
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        
        // Update session ID
        if (data.sessionId) {
            sessionId = data.sessionId;
            localStorage.setItem('triage_session_id', sessionId);
        }
        
        // Remove thinking bubble
        thinkingBubble.remove();
        
        // Add AI response
        addBubble(data.reply, 'bot');
        
        // Add sources if available
        addSources(data.sources);
        
        // Handle emergency state
        setEmergencyMode(data.emergency);
        
        // Log the interaction
        console.log('Triage Response:', {
            classification: data.route,
            emergency: data.emergency,
            sources: data.sources?.length || 0
        });
        
    } catch (error) {
        console.error('Chat error:', error);
        
        // Remove thinking bubble
        thinkingBubble.remove();
        
        // Show error message
        addBubble(
            '❌ I\'m experiencing technical difficulties. Please try again in a moment. ' +
            'If this is urgent, please contact emergency services directly.',
            'bot'
        );
    } finally {
        setLoading(false);
        input.focus();
    }
}

/**
 * Add chip text to input
 */
function addChipToInput(chipText) {
    const currentValue = input.value.trim();
    input.value = currentValue ? `${currentValue} ${chipText}` : chipText;
    input.focus();
}

// Event Listeners
document.addEventListener('DOMContentLoaded', () => {
    // Send button click
    sendButton.addEventListener('click', sendMessage);
    
    // Enter key to send
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // Quick action chips
    document.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => {
            addChipToInput(chip.dataset.add);
        });
    });
    
    // Focus input on load
    input.focus();
    
    // Add some example prompts if no session
    if (!sessionId) {
        setTimeout(() => {
            const exampleDiv = document.createElement('div');
            exampleDiv.className = 'sources';
            exampleDiv.innerHTML = `
                <strong>💡 Example symptoms to try:</strong><br>
                • "I have severe chest pain and shortness of breath"<br>
                • "Mild headache and runny nose for 2 days"<br>
                • "Recurring back pain for 3 weeks"<br>
                • "What's the capital of France" (to test non-medical handling)
            `;
            chat.appendChild(exampleDiv);
            scrollToBottom();
        }, 1000);
    }
});

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    // Focus input with Ctrl+/
    if (e.ctrlKey && e.key === '/') {
        e.preventDefault();
        input.focus();
    }
    
    // Clear chat with Ctrl+Shift+C
    if (e.ctrlKey && e.shiftKey && e.key === 'C') {
        e.preventDefault();
        if (confirm('Clear chat history?')) {
            const bubbles = chat.querySelectorAll('.bubble:not(.welcome-message .bubble)');
            const sources = chat.querySelectorAll('.sources');
            bubbles.forEach(b => b.remove());
            sources.forEach(s => s.remove());
            setEmergencyMode(false);
        }
    }
});

// Auto-resize input (future enhancement)
input.addEventListener('input', () => {
    // Could add auto-resize logic here
});

console.log('🏥 Medical Triage Assistant Web Interface Ready');
console.log('💡 Keyboard shortcuts: Ctrl+/ (focus input), Ctrl+Shift+C (clear chat)');