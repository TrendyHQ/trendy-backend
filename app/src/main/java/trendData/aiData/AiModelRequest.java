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
    /**
     * Generates trend analysis data using an AI model based on user information and
     * preferences.
     * The method connects to the Azure OpenAI service and uses the Mistral-Nemo
     * model to generate
     * responses about current or future trends tailored to the user's demographics.
     * 
     * @param message         The user's query or message about trends they want
     *                        information on
     * @param userLocation    The user's location coordinates in string format
     *                        (expected format: "latitude,longitude")
     * @param userBirthdate   The user's birthdate in a format that can be processed
     *                        by calculateAge method
     * @param userGender      The user's gender identification
     * @param isFutureRequest Boolean flag indicating whether the request is for
     *                        future trends (true)
     *                        or current trends (false)
     * @return A string containing the AI-generated response about trends relevant
     *         to the user.
     *         Returns error messages if the AI service connection fails.
     * @throws Exception May throw exceptions related to API connection,
     *                   authentication, or response processing
     */
    public String getAiData(String message, String userLocation, String userBirthdate, String userGender,
            boolean isFutureRequest) {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("GITHUB_TOKEN");
        String endpoint = "https://models.inference.ai.azure.com";
        String model = "Mistral-Nemo";

        // Preprocess user data: convert birthdate to age and coordinates to a friendly
        // location name
        try {
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
                    systemMessage = "You are a specialized future trend analyst providing accurate predictions about upcoming trends. "
                            +
                            "Consider the following user demographics: Location: " + locationName + ", Age: " + age
                            + ", Gender: " + userGender + ". " +
                            "Do not reveal these details in your response. " +
                            "Your analysis should: " +
                            "1. Cover multiple categories (technology, fashion, entertainment, social media, etc.) relevant to the user "
                            +
                            "2. Provide reasoning and evidence for each predicted trend " +
                            "3. Indicate a confidence level (high/medium/low) for each prediction " +
                            "4. Focus on a 1-3 year time horizon unless otherwise specified " +
                            "5. Consider regional and demographic relevance " +
                            "You are NOT allowed to answer questions about current trends. Only focus on future developments.";
                } else {
                    systemMessage = "You are a specialized current trend analyst providing accurate information about existing trends. "
                            +
                            "Consider the following user demographics: Location: " + locationName + ", Age: " + age
                            + ", Gender: " + userGender + ". " +
                            "Do not reveal these details in your response. " +
                            "Your analysis should: " +
                            "1. Cover multiple categories (technology, fashion, entertainment, social media, etc.) relevant to the user "
                            +
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
            chatCompletionsOptions.setTopP(0.95); // Slightly higher topP to allow for some creativity while maintaining
                                                  // coherence

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
        } catch (Exception e) {
            e.printStackTrace();
            return "Error generating response, try again later";
        }
    }

    // Helper method to calculate age from birthdate (assumes format "yyyy-MM-dd")
    private int calculateAge(String birthdate) {
        LocalDate birthDate = LocalDate.parse(birthdate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    // Method to get city name from coordinates using GeoNames API
    /**
     * Retrieves the city name from geographic coordinates using the GeoNames API.
     * 
     * This method takes a string containing latitude and longitude coordinates
     * separated by a comma,
     * and attempts to find the nearest populated place using the GeoNames web
     * service.
     * 
     * @param coordinates A string in the format "latitude,longitude" (e.g.,
     *                    "47.6062,-122.3321")
     * @return A string containing the city name and country (e.g., "Seattle, United
     *         States"),
     *         or one of the following error messages:
     *         - "Unknown Location" if the coordinates are null, empty, or the API
     *         request fails
     *         - "Invalid Location Format" if the coordinates string is not properly
     *         formatted
     * @throws Exception May throw various exceptions during parsing or API
     *                   communication,
     *                   which are caught and handled internally (resulting in
     *                   "Unknown Location" return)
     */
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
