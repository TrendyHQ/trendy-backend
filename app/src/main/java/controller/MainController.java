package controller;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import auth0.UploadFile;
import dataManagement.FeedbackManager;
import dataManagement.UserManager;
import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import trendData.aiData.AiModelRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;
import trendData.storage.StorageManager;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

import structure.TrendyClasses.AiRequest;
import structure.TrendyClasses.CommentRequest;
import structure.TrendyClasses.FeedbackObject;
import structure.TrendyClasses.FeedbackRequest;
import structure.TrendyClasses.PostData;
import structure.TrendyClasses.RedditPost;
import structure.TrendyClasses.SpecificPost;
import structure.TrendyClasses.TrendSaveRequest;
import structure.TrendyClasses.UpdateUserRequest;

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

    RedditClientManager redditClientManager = new RedditClientManager();

    @PostMapping("/reddit/topReddit")
    public ResponseEntity<String> getTopRedditData(@RequestBody String requestAmount) throws SQLException {
        try {
            int amount = Integer.parseInt(requestAmount);
            RedditDataFetcher redditData = new RedditDataFetcher();

            String[] subreddits = { "fashion", "technology", "food", "entertainment", "socialmedia", 
                                    "fitness", "wellness", "music", "politics", "travel", "science", "sports" };

            // Map subreddit names to their request futures
            List<CompletableFuture<RedditPost[]>> futures = new ArrayList<>();
            int limitPerSubreddit = Math.round(amount / 3);
            for (String subreddit : subreddits) {
                futures.add(requestDataFromReddit(redditData, subreddit, redditClientManager, limitPerSubreddit));
            }

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Combine results from futures
            List<RedditPost> allPosts = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                            return new RedditPost[0];
                        }
                    })
                    .filter(data -> data != null)
                    .flatMap(Arrays::stream)
                    .filter(post -> post != null)
                    .collect(Collectors.toList());

            // Remove duplicates by post id, keeping the post with the highest score if duplicates exist
            Map<String, RedditPost> uniquePosts = new LinkedHashMap<>();
            for (RedditPost post : allPosts) {
                String postId = post.getId();
                if (!uniquePosts.containsKey(postId) || post.getScore() > uniquePosts.get(postId).getScore()) {
                    uniquePosts.put(postId, post);
                }
            }
            allPosts = new ArrayList<>(uniquePosts.values());

            // Sort posts by score in descending order
            allPosts.sort((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

            // Take top 'amount' posts
            RedditPost[] topPosts = allPosts.stream().limit(amount).toArray(RedditPost[]::new);

            return ResponseEntity.ok(new Gson().toJson(topPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
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

    @GetMapping("/data/trend")
    public ResponseEntity<String> getSpecificTrendData(@RequestParam String postId) throws SQLException {
        RedditDataFetcher redditData = new RedditDataFetcher();
        try {
            StorageManager storageManager = new StorageManager();

            SpecificPost post = redditData.getSpecificPost(postId, redditClientManager, true);
            PostData postData = new PostData(post.getTitle(), post.getScore(), post.getMoreInfo(), post.getLink(),
                    post.getId(), post.getCategory(), storageManager.getInformationOnPost(postId));

            return ResponseEntity.ok(new Gson().toJson(postData));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("Failed to receive data");
    }

    @PutMapping("/data/addCommentToPost")
    public ResponseEntity<String> addCommentToPost(@RequestBody CommentRequest request) {
        try {
            StorageManager storageManager = new StorageManager();
            storageManager.putCommentOnPost(request.getPostId(), request.getComment());

            return ResponseEntity.ok("Comment added successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to add comment");
        }
    }

    @PutMapping("/data/addLikeToPost")
    public ResponseEntity<String> addLikeToPost(@RequestBody String postId) {
        try {
            System.out.println(postId);
            StorageManager storageManager = new StorageManager();
            int updatedLikes = storageManager.putLikeOnPost(postId);

            return ResponseEntity.ok("Like added successfully. New amount: " + updatedLikes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to add like");
        }
    }

    @PutMapping("/data/addFeedbackToDatabase")
    public ResponseEntity<String> addFeedbackToDatabase(@RequestBody FeedbackRequest feedback) {
        try {
            FeedbackManager feedbackManager = new FeedbackManager();
            feedbackManager.addFeedbackToDatabase(new FeedbackObject(feedback.getUserId(), feedback.getFeedback()),
                    feedback.getIsReport());

            return ResponseEntity.ok("Feedback added successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to add feedback");
        }
    }

    @PostMapping("/ai/AiModelRequest")
    public ResponseEntity<String> getAiData(@RequestBody AiRequest request) {
        try {
            String userBirthDate = getUserProperty("birthDate", request.getUserId()).getBody();
            String userGender = getUserProperty("gender", request.getUserId()).getBody();

            AiModelRequest aiController = new AiModelRequest();
            String response = aiController.getAiData(request.getMessage(), request.getUserLocation(),
                    userBirthDate,
                    userGender, request.getIsFutureRequest());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok("Error generating response, please try again later or contact support.");
        }
    }

    UserManager userManager = new UserManager();

    @PatchMapping("/users/updateUserInformation")
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

    @PutMapping("/users/update-picture")
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

    @GetMapping("/users/getUserProperty")
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

        SpecificPost[] favoritePosts = redditData.getFavoritePosts(userId, redditClientManager);

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

    private CompletableFuture<RedditPost[]> requestDataFromReddit(RedditDataFetcher redditData, String subredditName,
            RedditClientManager redditClientManager, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int limit = Math.round(amount / 3);
                return redditData.getData(subredditName, redditClientManager, limit);
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
