package com.triage.retrieval;

import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.triage.messages.Messages.RetrievedChunk;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 2B: Medical Information Fetcher
 * Retrieves fresh medical data from trusted sources when local corpus insufficient
 * Provides comprehensive medical coverage for any symptom
 */
public class MedicalInfoFetcher {
    
    private final OkHttpClient httpClient;
    
    public MedicalInfoFetcher() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    }
    
    /**
     * Fetch comprehensive medical information for any symptom
     */
    public List<RetrievedChunk> fetchMedicalInfo(String symptom, String sessionId) {
        List<RetrievedChunk> chunks = new ArrayList<>();
        
        System.out.println("🌐 PHASE 2B: Fetching live medical data for: " + symptom);
        
        // Try multiple trusted sources
        RetrievedChunk nhsChunk = fetchFromNHS(symptom, sessionId);
        if (nhsChunk != null) {
            chunks.add(nhsChunk);
            System.out.println("✅ NHS data retrieved for: " + symptom);
        }
        
        RetrievedChunk mayoChunk = fetchFromMayoClinic(symptom, sessionId);
        if (mayoChunk != null) {
            chunks.add(mayoChunk);
            System.out.println("✅ Mayo Clinic data retrieved for: " + symptom);
        }
        
        RetrievedChunk medlinePlusChunk = fetchFromMedlinePlus(symptom, sessionId);
        if (medlinePlusChunk != null) {
            chunks.add(medlinePlusChunk);
            System.out.println("✅ MedlinePlus data retrieved for: " + symptom);
        }
        
        System.out.println("🌐 PHASE 2B: Total live medical chunks retrieved: " + chunks.size());
        
        return chunks;
    }
    
    /**
     * Fetch from NHS (most comprehensive and reliable)
     */
    private RetrievedChunk fetchFromNHS(String symptom, String sessionId) {
        try {
            String searchUrl = buildNHSUrl(symptom);
            String extractedContent = fetchAndParseContent(searchUrl, "nhs");
            
            if (extractedContent != null && extractedContent.length() > 100) {
                return new RetrievedChunk(
                    "live_nhs_" + sessionId,
                    extractedContent,
                    "NHS",
                    searchUrl,
                    determineCategory(extractedContent),
                    0.95 // High score for fresh, authoritative data
                );
            }
        } catch (Exception e) {
            System.err.println("🌐 NHS fetch failed for " + symptom + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Fetch from Mayo Clinic (excellent detailed explanations)
     */
    private RetrievedChunk fetchFromMayoClinic(String symptom, String sessionId) {
        try {
            String searchUrl = buildMayoUrl(symptom);
            String extractedContent = fetchAndParseContent(searchUrl, "mayo");
            
            if (extractedContent != null && extractedContent.length() > 100) {
                return new RetrievedChunk(
                    "live_mayo_" + sessionId,
                    extractedContent,
                    "Mayo Clinic",
                    searchUrl,
                    determineCategory(extractedContent),
                    0.92 // High score for detailed medical content
                );
            }
        } catch (Exception e) {
            System.err.println("🌐 Mayo Clinic fetch failed for " + symptom + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Fetch from MedlinePlus (government health information)
     */
    private RetrievedChunk fetchFromMedlinePlus(String symptom, String sessionId) {
        try {
            String searchUrl = buildMedlinePlusUrl(symptom);
            String extractedContent = fetchAndParseContent(searchUrl, "medlineplus");
            
            if (extractedContent != null && extractedContent.length() > 100) {
                return new RetrievedChunk(
                    "live_medlineplus_" + sessionId,
                    extractedContent,
                    "MedlinePlus",
                    searchUrl,
                    determineCategory(extractedContent),
                    0.90 // High score for government health data
                );
            }
        } catch (Exception e) {
            System.err.println("🌐 MedlinePlus fetch failed for " + symptom + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Generic content fetching and parsing
     */
    private String fetchAndParseContent(String url, String source) {
        try {
            Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Medical-Triage-Assistant)")
                .build();
                
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    return parseContentBySource(html, source);
                }
            }
        } catch (Exception e) {
            System.err.println("Content fetch failed for " + url + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Parse content based on source website structure
     */
    private String parseContentBySource(String html, String source) {
        try {
            Document doc = Jsoup.parse(html);
            StringBuilder content = new StringBuilder();
            
            switch (source.toLowerCase()) {
                case "nhs":
                    return parseNHSContent(doc);
                case "mayo":
                    return parseMayoContent(doc);
                case "medlineplus":
                    return parseMedlinePlusContent(doc);
                default:
                    return parseGenericContent(doc);
            }
        } catch (Exception e) {
            System.err.println("Content parsing failed: " + e.getMessage());
            return null;
        }
    }
    
    private String parseNHSContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // NHS specific selectors for medical content
        Elements careCards = doc.select(".nhsuk-care-card, .nhsuk-warning-callout");
        Elements mainContent = doc.select("main p, .nhsuk-body-l, .nhsuk-list li");
        
        // Prioritize warning/care cards (emergency info)
        for (Element element : careCards) {
            String text = element.text().trim();
            if (text.length() > 30 && text.length() < 500) {
                content.append(text).append(" ");
                if (content.length() > 800) break;
            }
        }
        
        // Add main content if not enough from care cards
        if (content.length() < 300) {
            for (Element element : mainContent) {
                String text = element.text().trim();
                if (text.length() > 30 && text.length() < 400) {
                    content.append(text).append(" ");
                    if (content.length() > 1000) break;
                }
            }
        }
        
        return content.toString().trim();
    }
    
    private String parseMayoContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // Mayo Clinic specific selectors
        Elements symptoms = doc.select(".symptoms, .causes, .when-to-see-doctor, .content");
        Elements mainContent = doc.select("main p, .content p");
        
        for (Element element : symptoms) {
            String text = element.text().trim();
            if (text.length() > 30 && text.length() < 500) {
                content.append(text).append(" ");
                if (content.length() > 1000) break;
            }
        }
        
        return content.toString().trim();
    }
    
    private String parseMedlinePlusContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // MedlinePlus specific selectors
        Elements healthInfo = doc.select(".health-summary, .page-info, .section");
        Elements mainContent = doc.select("main p, .content p");
        
        for (Element element : healthInfo) {
            String text = element.text().trim();
            if (text.length() > 30 && text.length() < 500) {
                content.append(text).append(" ");
                if (content.length() > 1000) break;
            }
        }
        
        return content.toString().trim();
    }
    
    private String parseGenericContent(Document doc) {
        StringBuilder content = new StringBuilder();
        
        // Generic content extraction
        Elements paragraphs = doc.select("p, li");
        
        for (Element element : paragraphs) {
            String text = element.text().trim();
            if (text.length() > 30 && text.length() < 400) {
                content.append(text).append(" ");
                if (content.length() > 1000) break;
            }
        }
        
        return content.toString().trim();
    }
    
    /**
     * Build search URLs for different medical sources
     */
    private String buildNHSUrl(String symptom) {
        String cleanSymptom = cleanSymptomForUrl(symptom);
        
        // Common NHS condition URLs
        if (cleanSymptom.contains("chest-pain")) {
            return "https://www.nhs.uk/conditions/chest-pain/";
        } else if (cleanSymptom.contains("vomiting") || cleanSymptom.contains("nausea")) {
            return "https://www.nhs.uk/conditions/vomiting-adults/";
        } else if (cleanSymptom.contains("back-pain")) {
            return "https://www.nhs.uk/conditions/back-pain/";
        } else if (cleanSymptom.contains("headache")) {
            return "https://www.nhs.uk/conditions/headaches/";
        } else if (cleanSymptom.contains("diarrhea") || cleanSymptom.contains("stomach")) {
            return "https://www.nhs.uk/conditions/diarrhoea-and-vomiting/";
        } else if (cleanSymptom.contains("breathing") || cleanSymptom.contains("shortness")) {
            return "https://www.nhs.uk/conditions/shortness-of-breath/";
        } else {
            // Generic NHS symptoms page
            return "https://www.nhs.uk/conditions/" + cleanSymptom + "/";
        }
    }
    
    private String buildMayoUrl(String symptom) {
        String cleanSymptom = cleanSymptomForUrl(symptom);
        
        // Common Mayo Clinic condition URLs
        if (cleanSymptom.contains("chest-pain")) {
            return "https://www.mayoclinic.org/diseases-conditions/chest-pain/symptoms-causes/syc-20370838";
        } else if (cleanSymptom.contains("vomiting")) {
            return "https://www.mayoclinic.org/symptoms/vomiting/basics/definition/sym-20050942";
        } else if (cleanSymptom.contains("back-pain")) {
            return "https://www.mayoclinic.org/diseases-conditions/back-pain/symptoms-causes/syc-20369906";
        } else if (cleanSymptom.contains("headache")) {
            return "https://www.mayoclinic.org/diseases-conditions/headaches/symptoms-causes/syc-20377913";
        } else {
            // Generic Mayo search
            return "https://www.mayoclinic.org/diseases-conditions/" + cleanSymptom;
        }
    }
    
    private String buildMedlinePlusUrl(String symptom) {
        String cleanSymptom = cleanSymptomForUrl(symptom);
        
        // MedlinePlus health topics
        if (cleanSymptom.contains("chest") || cleanSymptom.contains("heart")) {
            return "https://medlineplus.gov/chestpain.html";
        } else if (cleanSymptom.contains("vomiting") || cleanSymptom.contains("nausea")) {
            return "https://medlineplus.gov/nauseaandvomiting.html";
        } else if (cleanSymptom.contains("back")) {
            return "https://medlineplus.gov/backpain.html";
        } else if (cleanSymptom.contains("headache")) {
            return "https://medlineplus.gov/headache.html";
        } else {
            return "https://medlineplus.gov/healthtopics.html";
        }
    }
    
    /**
     * Clean symptom text for URL building
     */
    private String cleanSymptomForUrl(String symptom) {
        return symptom.toLowerCase()
            .replace("i have ", "")
            .replace("i am ", "")
            .replace("facing ", "")
            .replace("experiencing ", "")
            .replace("feeling ", "")
            .replace(" pain", "-pain")
            .replace(" ", "-")
            .replaceAll("[^a-zA-Z0-9-]", "");
    }
    
    /**
     * Determine medical category based on content analysis
     */
    private String determineCategory(String content) {
        String contentLower = content.toLowerCase();
        
        // Emergency indicators (red flags)
        if (contentLower.contains("call 911") || contentLower.contains("emergency") ||
            contentLower.contains("immediate") || contentLower.contains("urgent") ||
            contentLower.contains("life threatening") || contentLower.contains("seek immediate care") ||
            contentLower.contains("emergency room") || contentLower.contains("ambulance") ||
            contentLower.contains("critical") || contentLower.contains("severe") ||
            contentLower.contains("heart attack") || contentLower.contains("stroke") ||
            contentLower.contains("breathing difficulty") || contentLower.contains("chest pain")) {
            return "red_flag";
        }
        
        // Self-care indicators
        if (contentLower.contains("home treatment") || contentLower.contains("self care") ||
            contentLower.contains("rest") || contentLower.contains("over-the-counter") ||
            contentLower.contains("home remedies") || contentLower.contains("usually resolves") ||
            contentLower.contains("mild") || contentLower.contains("minor") ||
            contentLower.contains("self-limiting") || contentLower.contains("home management")) {
            return "self_care";
        }
        
        // Default to appointment for unclear cases
        return "appointment";
    }
    
    /**
     * Extract symptoms from complex user input
     */
    public String extractPrimarySymptom(String userInput) {
        String input = userInput.toLowerCase();
        
        // Common symptom patterns
        if (input.contains("chest pain") || input.contains("heart pain")) {
            return "chest pain";
        } else if (input.contains("vomiting") || input.contains("throwing up") || input.contains("nausea")) {
            return "vomiting";
        } else if (input.contains("back pain") || input.contains("spine")) {
            return "back pain";
        } else if (input.contains("headache") || input.contains("head pain")) {
            return "headache";
        } else if (input.contains("breathing") || input.contains("shortness of breath") || input.contains("dyspnea")) {
            return "shortness of breath";
        } else if (input.contains("diarrhea") || input.contains("loose stools")) {
            return "diarrhea";
        } else if (input.contains("fever") || input.contains("temperature")) {
            return "fever";
        } else if (input.contains("cough") || input.contains("coughing")) {
            return "cough";
        } else if (input.contains("dizziness") || input.contains("dizzy")) {
            return "dizziness";
        } else if (input.contains("stomach") || input.contains("abdominal")) {
            return "abdominal pain";
        } else {
            // Extract first medical-sounding term
            String[] words = input.split("\\s+");
            for (String word : words) {
                if (word.length() > 4 && 
                    (word.contains("pain") || word.contains("ache") || 
                     word.contains("hurt") || word.contains("sick"))) {
                    return word;
                }
            }
        }
        
        return userInput.trim(); // Fallback to original input
    }
    
    /**
     * Cleanup and format extracted medical content
     */
    private String cleanMedicalContent(String rawContent) {
        if (rawContent == null) return "";
        
        return rawContent
            .replaceAll("\\s+", " ") // Normalize whitespace
            .replaceAll("Cookie policy.*", "") // Remove cookie notices
            .replaceAll("Privacy policy.*", "") // Remove privacy notices
            .replaceAll("Advertisement.*", "") // Remove ads
            .trim();
    }
    
    /**
     * Build search term for better medical site compatibility
     */
    private String buildSearchTerm(String symptom) {
        return symptom.toLowerCase()
            .replace("i have ", "")
            .replace("i am ", "")
            .replace("experiencing ", "")
            .replace("severe ", "")
            .replace("mild ", "")
            .trim();
    }
    
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
}