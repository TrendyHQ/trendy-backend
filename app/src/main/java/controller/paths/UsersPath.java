package controller.paths;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import auth0.UploadFile;
import dataManagement.UserManager;
import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.FavoritePostObject;
import structure.TrendyClasses.SpecificPost;
import structure.TrendyClasses.TrendSaveRequest;
import structure.TrendyClasses.UpdateUserRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

@RestController
@RequestMapping("/api/users") // Define the base URL for your endpoints
public class UsersPath {
    Dotenv dotenv = Dotenv.load();

    final String DOMAIN = dotenv.get("AUTH0_DOMAIN");
    final String CLIENT_ID = dotenv.get("MANAGEMENT_AUTH0_CLIENT_ID");
    final String CLIENT_SECRET = dotenv.get("MANAGEMENT_AUTH0_CLIENT_SECRET");

    UserManager userManager = new UserManager();
    RedditClientManager redditClientManager = new RedditClientManager();

    @PatchMapping("/updateUserInformation")
    public ResponseEntity<String> updateUserInformation(@RequestBody UpdateUserRequest request) {
        String[] editableUserProperties = {
                "blocked", // Boolean flag indicating if the user is blocked
                "email", // The user's email address
                "email_verified", // Boolean indicating if the email has been verified
                "username", // The user's username (typically for database connections)
                "password", // The user's password (handle securely)
                "phone_number", // The user's phone number
                "phone_verified", // Boolean indicating if the phone number has been verified
                "given_name", // The user's first name
                "family_name", // The user's last name
                "name", // The user's full name
                "nickname", // The user's nickname
                "picture", // URL to the user's avatar image
                "user_metadata", // Custom, user-specific metadata (object)
                "app_metadata", // Custom metadata used for application-specific info (object)
                "multifactor" // Array for multi-factor authentication providers
        };

        try {
            String accessToken = getAccessToken();

            JsonObject updateJson = JsonParser.parseString(request.getToUpdate()).getAsJsonObject();
            JsonObject filteredJson = new JsonObject();

            // Iterate through each entry in the updateJson object
            for (Map.Entry<String, JsonElement> entry : updateJson.entrySet()) {
                JsonElement value = entry.getValue();
                // Only proceed if the value is not null
                if (!value.isJsonNull() && Arrays.asList(editableUserProperties).contains(entry.getKey())) {
                    // Check if the value is a JSON object (nested structure)
                    if (value.isJsonObject()) {
                        JsonObject nestedObj = value.getAsJsonObject();
                        JsonObject filteredNested = new JsonObject();
                        // Iterate through each entry in the nested JSON object
                        for (Map.Entry<String, JsonElement> nestedEntry : nestedObj.entrySet()) {
                            JsonElement nestedValue = nestedEntry.getValue();
                            // Skip nested entry if its value is null
                            if (!nestedValue.isJsonNull()) {
                                // If the nested value is a primitive string, check for non-emptiness
                                if (nestedValue.isJsonPrimitive() && nestedValue.getAsJsonPrimitive().isString()) {
                                    if (!nestedValue.getAsString().isEmpty()) {
                                        // Add non-empty string to the filtered nested object
                                        filteredNested.add(nestedEntry.getKey(), nestedValue);
                                    }
                                } else {
                                    // For non-string primitives or other types, add them directly
                                    filteredNested.add(nestedEntry.getKey(), nestedValue);
                                }
                            }
                        }
                        // If the filtered nested object has entries, add it to the filteredJson
                        if (filteredNested.entrySet().size() > 0) {
                            filteredJson.add(entry.getKey(), filteredNested);
                        }
                    } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        // If the value is a primitive string, add it only if it's not empty
                        if (!value.getAsString().isEmpty()) {
                            filteredJson.add(entry.getKey(), value);
                        }
                    } else {
                        // For all other types, add the value directly to the filteredJson
                        filteredJson.add(entry.getKey(), value);
                    }
                }
            }

            String cleanedUpdate = filteredJson.toString();

            setUserInformation(cleanedUpdate, accessToken, request.getUserId());

            return ResponseEntity.ok(cleanedUpdate); // Update response to return cleanedUpdate instead of
                                                     // request.getToUpdate()
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }

    @PutMapping("/update-picture")
    public ResponseEntity<String> updatePicture(

            @RequestParam String userId,

            @RequestPart("file") MultipartFile file) throws Exception {
        try {
            MultipartFile newPicture = file;

            String fileUrl = new UploadFile().uploadToS3(newPicture);

            String accessToken = getAccessToken();

            JsonObject requestBodyJson = new JsonObject();
            requestBodyJson.addProperty("picture", fileUrl);
            String requestBody = requestBodyJson.toString();

            setUserInformation(requestBody, accessToken, userId);

            return ResponseEntity.ok("Picture updated successfully");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to update picture");
        }

    }

    @GetMapping("/getUserProperty")
    public ResponseEntity<String> getUserProperty(@RequestParam String property, @RequestParam String userId) {
        String[] validProperties = {
                "picture",
        };
        String[] validAppMetaDataProperties = {
                "hasSetUpAccount",
        };
        String[] validUserMetaDataProperties = {
                "gender",
                "birthDate",
        };

        try {
            if (Arrays.asList(validProperties).contains(property)) {
                JsonObject jsonResponse = getAuth0Info(userId);
                String requestedProperty = jsonResponse.get(property).getAsString();

                return ResponseEntity.ok(requestedProperty);
            } else {
                if (Arrays.asList(validAppMetaDataProperties).contains(property)) {
                    JsonObject jsonResponse = getAuth0Info(userId);
                    String requestedProperty = "";

                    if (jsonResponse.has("app_metadata") &&
                            jsonResponse.get("app_metadata").getAsJsonObject().has(property)) {
                        requestedProperty = jsonResponse.get("app_metadata").getAsJsonObject().get(property)
                                .getAsString();
                    }

                    return ResponseEntity.ok(requestedProperty);
                } else if (Arrays.asList(validUserMetaDataProperties).contains(property)) {
                    JsonObject jsonResponse = getAuth0Info(userId);
                    String requestedProperty = "";

                    if (jsonResponse.has("user_metadata") &&
                            jsonResponse.get("user_metadata").getAsJsonObject().has(property)) {
                        requestedProperty = jsonResponse.get("user_metadata").getAsJsonObject().get(property)
                                .getAsString();
                    }

                    return ResponseEntity.ok(requestedProperty);
                } else {
                    return ResponseEntity.badRequest().body("Invalid user property");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to get user property");
        }
    }

    @PatchMapping("/saveTrend")
    public ResponseEntity<String> saveTrend(@RequestBody TrendSaveRequest request) {
        try {
            userManager.saveTrendForUser(request.getUserId(), request.getTrendId(), request.getSaveTrend(),
                    request.getTrendCategory());

            return ResponseEntity.ok("Trend saved successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to save trend");
        }
    }

    @GetMapping("/getSavedTrends")
    public ResponseEntity<String> getSavedTrends(@RequestParam String userId) {
        ArrayList<FavoritePostObject> savedTrends = userManager.getUsersFavoritePostsIds(userId);
        String jsonSavedTrends = new Gson().toJson(savedTrends.toArray());
        return ResponseEntity.ok(jsonSavedTrends);
    }

    @GetMapping("/getUsersTrends")
    public ResponseEntity<String> getUsersTrends(@RequestParam String userId) throws SQLException {
        RedditDataFetcher redditData = new RedditDataFetcher();

        if (redditClientManager.getClient() == null) {
            redditClientManager.autherizeClient();
        }

        RedditClient redditClient = redditClientManager.getClient();

        SpecificPost[] favoritePosts = redditData.getFavoritePosts(userId, redditClient);

        String jsonResponse = new Gson().toJson(favoritePosts);

        return ResponseEntity.ok(jsonResponse);
    }

    private String getAccessToken() throws Exception {

        String jsonBody = "{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"audience\":\"https://" + DOMAIN + "/api/v2/\",\"grant_type\":\"client_credentials\"}";

        HttpResponse<String> response = Unirest.post("https://" + DOMAIN + "/oauth/token")
                .header("content-type", "application/json")
                .body(jsonBody)
                .asString();

        JsonObject jsonResponse = JsonParser.parseString(response.getBody()).getAsJsonObject();
        String accessToken = jsonResponse.get("access_token").getAsString();

        return accessToken;
    }

    private void setUserInformation(String requestBody, String accessToken, String userId) throws Exception {
        String encodedUserId;
        if (userId.contains("%")) {
            encodedUserId = userId;
        } else {
            encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());
        }

        @SuppressWarnings("unused")
        HttpResponse<String> auth0ApiResponse = Unirest
                .patch("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                .header("authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("cache-control", "no-cache")
                .body(requestBody)
                .asString();

    }

    private JsonObject getAuth0Info(String userId) throws Exception {
        String accessToken = getAccessToken();

        String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());
        HttpResponse<String> auth0ApiResponse = Unirest
                .get("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                .header("authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("cache-control", "no-cache")
                .asString();

        return JsonParser.parseString(auth0ApiResponse.getBody()).getAsJsonObject();
    }
}
