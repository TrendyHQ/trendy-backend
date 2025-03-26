package controller.paths;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import structure.TrendyClasses.AiRequest;
import trendData.aiData.AiModelRequest;

@RestController
@RequestMapping("api/ai") // Define the base URL for your endpoints
public class AiPath {
    /**
     * Processes an AI data request and generates a response based on user attributes and message content.
     * 
     * @param request AiRequest object containing userId, message, userLocation, and isFutureRequest flag
     * @return ResponseEntity with AI-generated response or error message
     */
    @PostMapping("/AiModelRequest")
    public ResponseEntity<String> getAiData(@RequestBody AiRequest request) {
        try {
            UsersPath usersPath = new UsersPath();
            String userBirthDate = usersPath.getUserProperty("birthDate", request.getUserId()).getBody();
            String userGender = usersPath.getUserProperty("gender", request.getUserId()).getBody();

            AiModelRequest aiController = new AiModelRequest();
            String response = aiController.getAiData(request.getMessage(), request.getUserLocation(),
                    userBirthDate,
                    userGender, request.getIsFutureRequest());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error generating response, please try again later or contact support.");
        }
    }
}
