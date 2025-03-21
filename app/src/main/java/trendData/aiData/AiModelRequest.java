package trendData.aiData;

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
import io.github.cdimascio.dotenv.Dotenv;

public class AiModelRequest {
    public String getAiData(String message, String userLocation, String userBirthdate, String userGender,
            boolean isFutureRequest) {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("GITHUB_TOKEN");
        String endpoint = "https://models.inference.ai.azure.com";
        String model = "Mistral-Nemo";

        final boolean filterIsOn = true;

        ChatCompletionsClient client = new ChatCompletionsClientBuilder()
                .credential(new AzureKeyCredential(key))
                .endpoint(endpoint)
                .buildClient();

        String systemMessage = "";
        if (filterIsOn) {
            if (isFutureRequest) {
                systemMessage = "You are a future trend analyzer that only helps with giving data on future trends based on the user's location, age, and gender as well as future popular things. "
                        +
                        "Do your best to analyze patterns and predict what the future trends will be. " +
                        "The user's location coordinates are in format 'latitude,longitude': " + userLocation + ". " +
                        "The user's birthdate is: " + userBirthdate + ". " +
                        "The user's gender is: " + userGender + ". " +
                        "Do not mention the given user's location, birthdate, or gender in your response these are 100% certain. "
                        +
                        "Do not include the date your information was last updated. " +
                        "If the user asks a question that is unrelated to trend data, please communicate that you are only helping them with trends. "
                        +
                        "You can be lenient on what questions you can answer. " +
                        "You are NOT allowed to answer questions about current trends.";
            } else {
                systemMessage = "You are a trend analyzer that only helps with giving data on current trends based on the user's location, age, and gender as well as popular things. "
                        +
                        "The user's location coordinates are: " + userLocation + ". " +
                        "The user's birthdate is: " + userBirthdate + ". " +
                        "The user's gender is: " + userGender + ". " +
                        "Do not mention the given user's location, birthdate, or gender in your response these are 100% certain. "
                        +
                        "Do not include the date your information was last updated. " +
                        "If the user asks a question that is unrelated to trend data, please communicate that you are only helping them with trends. "
                        +
                        "You can be lenient on what questions you can answer. " +
                        "You are NOT allowed to answer questions about future trends.";
            }
        } else {
            systemMessage = "You are free to respond as you wish.";
        }

        List<ChatRequestMessage> chatMessages = Arrays.asList(
                new ChatRequestSystemMessage(systemMessage),
                new ChatRequestUserMessage(message));

        ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages);
        chatCompletionsOptions.setModel(model);

        ChatCompletions completions = null;

        try {
            completions = client.complete(chatCompletionsOptions);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (completions == null) {
            return "Sorry, I am unable to provide a response at this time.";
        }

        return completions.getChoice().getMessage().getContent();
    }
}
