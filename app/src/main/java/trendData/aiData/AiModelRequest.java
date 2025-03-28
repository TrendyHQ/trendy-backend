package trendData.aiData;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import com.azure.ai.inference.ChatCompletionsClient;
import com.azure.ai.inference.ChatCompletionsClientBuilder;
import com.azure.ai.inference.models.ChatCompletions;
import com.azure.ai.inference.models.ChatCompletionsOptions;
import com.azure.ai.inference.models.ChatRequestMessage;
import com.azure.ai.inference.models.ChatRequestSystemMessage;
import com.azure.ai.inference.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import io.github.cdimascio.dotenv.Dotenv;

public class AiModelRequest {
    public String getAiData(String message, String userLocation, String userBirthdate, String userGender,
            boolean isFutureRequest) {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("GITHUB_TOKEN");
        String endpoint = "https://models.inference.ai.azure.com";
        String model = "Mistral-Nemo";

        // Preprocess user data: convert birthdate to age and coordinates to a friendly location name
        int age = calculateAge(userBirthdate);
        String locationName = getCityFromCoordinates(userLocation);

        final boolean filterIsOn = true;

        ChatCompletionsClient client = new ChatCompletionsClientBuilder()
                .credential(new AzureKeyCredential(key))
                .endpoint(endpoint)
                .buildClient();

        String systemMessage;
        if (filterIsOn) {
            if (isFutureRequest) {
                systemMessage = "You are a specialized future trend analyst providing accurate predictions about upcoming trends. " +
                        "Consider the following user demographics: Location: " + locationName + ", Age: " + age + ", Gender: " + userGender + ". " +
                        "Do not reveal these details in your response. " +
                        "Your analysis should: " +
                        "1. Cover multiple categories (technology, fashion, entertainment, social media, etc.) relevant to the user " +
                        "2. Provide reasoning and evidence for each predicted trend " +
                        "3. Indicate a confidence level (high/medium/low) for each prediction " +
                        "4. Focus on a 1-3 year time horizon unless otherwise specified " +
                        "5. Consider regional and demographic relevance " +
                        "You are NOT allowed to answer questions about current trends. Only focus on future developments.";
            } else {
                systemMessage = "You are a specialized current trend analyst providing accurate information about existing trends. " +
                        "Consider the following user demographics: Location: " + locationName + ", Age: " + age + ", Gender: " + userGender + ". " +
                        "Do not reveal these details in your response. " +
                        "Your analysis should: " +
                        "1. Cover multiple categories (technology, fashion, entertainment, social media, etc.) relevant to the user " +
                        "2. Provide specific examples and evidence for each trend " +
                        "3. Indicate how established each trend is (emerging/mainstream/declining) " +
                        "4. Consider regional and demographic relevance " +
                        "5. Focus on factual information rather than speculation " +
                        "You are NOT allowed to answer questions about future trends. Only focus on current patterns.";
            }
        } else {
            systemMessage = "You are free to respond as you wish.";
        }

        List<ChatRequestMessage> chatMessages = Arrays.asList(
                new ChatRequestSystemMessage(systemMessage),
                new ChatRequestUserMessage(message));

        ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages);
        chatCompletionsOptions.setModel(model);
        
        // Set optimal parameters for accurate trend analysis
        chatCompletionsOptions.setTemperature(0.3); // Lower temperature for more focused, factual responses
        chatCompletionsOptions.setTopP(0.95); // Slightly higher topP to allow for some creativity while maintaining coherence

        ChatCompletions completions = null;
        try {
            completions = client.complete(chatCompletionsOptions);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }

        if (completions == null || completions.getChoice() == null) {
            return "Sorry, I am unable to provide a response at this time.";
        }

        return completions.getChoice().getMessage().getContent();
    }

    // Helper method to calculate age from birthdate (assumes format "yyyy-MM-dd")
    private int calculateAge(String birthdate) {
        LocalDate birthDate = LocalDate.parse(birthdate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    // Method to get city name from coordinates using GeoNames API
    private String getCityFromCoordinates(String coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return "Unknown Location";
        }
        
        try {
            Dotenv dotenv = Dotenv.load();
            
            String[] parts = coordinates.split(",");
            if (parts.length != 2) {
                return "Invalid Location Format";
            }
            
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            
            // Call GeoNames API to get place name based on coordinates
            HttpResponse<String> geonamesResponse = Unirest
                    .get("http://api.geonames.org/findNearbyPlaceNameJSON")
                    .queryString("lat", latitude)
                    .queryString("lng", longitude)
                    .queryString("username", dotenv.get("GEONAMES_USERNAME"))
                    .asString();
            
            if (geonamesResponse.getStatus() != 200) {
                return "Unknown Location";
            }
            
            JsonObject responseData = JsonParser.parseString(geonamesResponse.getBody()).getAsJsonObject();
            if (responseData.has("geonames") && responseData.getAsJsonArray("geonames").size() > 0) {
                JsonObject placeData = responseData.getAsJsonArray("geonames").get(0).getAsJsonObject();
                String cityName = placeData.has("name") ? placeData.get("name").getAsString() : "Unknown City";
                String countryName = placeData.has("countryName") ? placeData.get("countryName").getAsString() : "";
                
                return countryName.isEmpty() ? cityName : cityName + ", " + countryName;
            }
            
            return "Unknown Location";
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown Location";
        }
    }
}
