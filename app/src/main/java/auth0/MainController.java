package auth0;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dataManagement.UserManager;
import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import trendData.aiData.AiModelRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;
import trendData.redditData.RedditDataFetcher.FavoritePost;
import trendData.redditData.RedditDataFetcher.RedditPost;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@RestController
@RequestMapping("/api") // Define the base URL for your endpoints
public class MainController {
    public static void main(String[] args) {
        SpringApplication.run(MainController.class, args);
    }

    Dotenv dotenv = Dotenv.load();

    final String DOMAIN = dotenv.get("AUTH0_DOMAIN");
    final String CLIENT_ID = dotenv.get("MANAGEMENT_AUTH0_CLIENT_ID");
    final String CLIENT_SECRET = dotenv.get("MANAGEMENT_AUTH0_CLIENT_SECRET");

    @PatchMapping("/auth0/updateUserInformation")
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

    @PutMapping("/auth0/update-picture")
    public ResponseEntity<String> updatePicture(

            @RequestParam("userId") String userId,

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

    @GetMapping("/auth0/getUserProperty")
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

    RedditClientManager redditClientManager = new RedditClientManager();

    @PostMapping("/reddit/topReddit")
    public ResponseEntity<String> getTopRedditData() throws SQLException {
        try {
            RedditDataFetcher redditData = new RedditDataFetcher();

            CompletableFuture<RedditPost[]> fashionFuture = requestDataFromReddit(redditData, "fashion",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> technologyFuture = requestDataFromReddit(redditData, "technology",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> foodFuture = requestDataFromReddit(redditData, "food", redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> entertainmentFuture = requestDataFromReddit(redditData, "entertainment",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> socialMediaFuture = requestDataFromReddit(redditData, "socialmedia",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> fitnessFuture = requestDataFromReddit(redditData, "fitness",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> wellnessFuture = requestDataFromReddit(redditData, "wellness",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> musicFuture = requestDataFromReddit(redditData, "music",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> politicsFuture = requestDataFromReddit(redditData, "politics",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> travelFuture = requestDataFromReddit(redditData, "travel",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> scienceFuture = requestDataFromReddit(redditData, "science",
                    redditClientManager);
            waitForSeconds();
            CompletableFuture<RedditPost[]> sportsFuture = requestDataFromReddit(redditData, "sports",
                    redditClientManager);

            CompletableFuture.allOf(
                    fashionFuture, technologyFuture, foodFuture, entertainmentFuture,
                    socialMediaFuture, fitnessFuture, wellnessFuture, musicFuture,
                    politicsFuture, travelFuture, scienceFuture, sportsFuture).join();

            RedditPost[] fashionData = fashionFuture.get();
            RedditPost[] technologyData = technologyFuture.get();
            RedditPost[] foodData = foodFuture.get();
            RedditPost[] entertainmentData = entertainmentFuture.get();
            RedditPost[] socialMediaData = socialMediaFuture.get();
            RedditPost[] fitnessData = fitnessFuture.get();
            RedditPost[] wellnessData = wellnessFuture.get();
            RedditPost[] musicData = musicFuture.get();
            RedditPost[] politicsData = politicsFuture.get();
            RedditPost[] travelData = travelFuture.get();
            RedditPost[] scienceData = scienceFuture.get();
            RedditPost[] sportsData = sportsFuture.get();

            RedditPost[][] data = {
                    fashionData, technologyData, foodData, entertainmentData, socialMediaData,
                    fitnessData, wellnessData, musicData, politicsData, travelData, scienceData, sportsData
            };

            // Collect all posts into a single list
            List<RedditPost> allPosts = new ArrayList<>();
            for (RedditPost[] subredditData : data) {
                if (subredditData != null) { // Ensure that the subredditData is not null
                    Collections.addAll(allPosts, subredditData);
                }
            }

            // Sort posts by score in descending order
            allPosts.sort((p1, p2) -> {
                if (p1 != null && p2 != null) {
                    return Integer.compare(p2.getScore(), p1.getScore());
                }
                return 0; // If either p1 or p2 is null, consider them equal for sorting purposes
            });

            // Take top 6 posts or as many as are available
            RedditPost[] topPosts = new RedditPost[Math.min(6, allPosts.size())];
            for (int i = 0; i < topPosts.length; i++) {
                topPosts[i] = allPosts.get(i);
            }

            return ResponseEntity.ok(new Gson().toJson(topPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to recieve data");
        }
    }

    @PostMapping("/reddit/topTrendsForCategory")
    public ResponseEntity<String> getTopTrendsForCategory(@RequestBody String entity) {
        try {
            int limit = 30;
            RedditDataFetcher redditData = new RedditDataFetcher();
            RedditPost[] posts = redditData.getData(entity, redditClientManager, limit);

            // Collect all posts into a single list
            List<RedditPost> allPosts = new ArrayList<>();
            if (posts != null) {
                Collections.addAll(allPosts, posts);
            }

            // Sort posts by score in descending order
            allPosts.sort((p1, p2) -> {
                if (p1 != null && p2 != null) {
                    return Integer.compare(p2.getScore(), p1.getScore());
                }
                return 0; // If either p1 or p2 is null, consider them equal for sorting purposes
            });

            return ResponseEntity.ok(new Gson().toJson(allPosts));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to recieve data");
        }
    }

    @PostMapping("/ai/AiModelRequest")
    public ResponseEntity<String> getPhi4Data(@RequestBody AiRequest request) {
        try {
            AiModelRequest phi4 = new AiModelRequest();
            String response = phi4.getPhi4Data(request.getMessage(), request.getUserLocation(),
                    request.getUserBirthdate(),
                    request.getUserGender(), request.getIsFutureRequest());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok("Error generating response, please try again later or contact support.");
        }
    }

    UserManager userManager = new UserManager();

    @PatchMapping("/users/saveTrend")
    public ResponseEntity<String> saveTrend(@RequestBody TrendSaveRequest request) {
        try {
            userManager.saveTrendForUser(request.getUserId(), request.getTrendId(), request.getSaveTrend());

            return ResponseEntity.ok("Trend saved successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to save trend");
        }
    }

    @GetMapping("/users/getSavedTrends")
    public ResponseEntity<String> getSavedTrends(@RequestParam String userId) {
        ArrayList<String> savedTrends = userManager.getUsersFavoritePostsIds(userId);
        String jsonSavedTrends = new Gson().toJson(savedTrends.toArray());
        return ResponseEntity.ok(jsonSavedTrends);
    }

    @GetMapping("/users/getUsersTrends")
    public ResponseEntity<String> getUsersTrends(@RequestParam String userId) throws SQLException {
        RedditDataFetcher redditData = new RedditDataFetcher();
    
        FavoritePost[] favoritePosts = redditData.getFavoritePosts(userId, redditClientManager);

        String jsonResponse = new Gson().toJson(favoritePosts);

        return ResponseEntity.ok(jsonResponse);
    }

    public static class UserUpdateRequest {
        private String userId;
        private String newNickname;

        public String getUserId() {
            return userId;
        }

        public String getNewNickname() {
            return newNickname;
        }
    }

    public static class AiRequest {
        private String message;
        private String userLocation;
        private String userBirthdate;
        private String userGender;
        private boolean isFutureRequest;

        public String getMessage() {
            return message;
        }

        public String getUserLocation() {
            return userLocation;
        }

        public String getUserBirthdate() {
            return userBirthdate;
        }

        public String getUserGender() {
            return userGender;
        }

        public boolean getIsFutureRequest() {
            return isFutureRequest;
        }
    }

    public static class LoginRequest {
        private String userId;

        public String getUserId() {
            return userId;
        }
    }

    public static class GenderUpdateRequest {
        private String userId;
        private String gender;

        public String getUserId() {
            return userId;
        }

        public String getGender() {
            return gender;
        }
    }

    public static class TrendSaveRequest {
        private String userId;
        private String trendId;
        private boolean saveTrend;

        public String getUserId() {
            return userId;
        }

        public String getTrendId() {
            return trendId;
        }

        public boolean getSaveTrend() {
            return saveTrend;
        }
    }

    public static class UpdateUserRequest {
        private String userId;
        private String toUpdate;

        public String getUserId() {
            return userId;
        }

        public String getToUpdate() {
            return toUpdate;
        }
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

    private CompletableFuture<RedditPost[]> requestDataFromReddit(RedditDataFetcher redditData, String subredditName,
            RedditClientManager redditClientManager) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return redditData.getData(subredditName, redditClientManager, 2);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    private void waitForSeconds() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
