package auth0;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import trendData.aiData.AiModelRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.TopRedditData;
import trendData.redditData.TopRedditData.RedditPost;

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

    final String DOMAIN = dotenv.get("VITE_AUTH0_DOMAIN");
    final String CLIENT_ID = dotenv.get("VITE_MANAGEMENT_AUTH0_CLIENT_ID");
    final String CLIENT_SECRET = dotenv.get("VITE_MANAGEMENT_AUTH0_CLIENT_SECRET");

    /*
     * @PutMapping("/auth0/update-nickname")
     * public ResponseEntity<String> publicEndpoint(@RequestBody UserUpdateRequest
     * request) throws Exception {
     * try {
     * 
     * String userId = request.getUserId();
     * String newNickname = request.getNewNickname();
     * 
     * String accessToken = getAccessToken();
     * 
     * JsonObject requestBodyJson = new JsonObject();
     * requestBodyJson.addProperty("nickname", newNickname);
     * String requestBody = requestBodyJson.toString();
     * 
     * setUserProperty(requestBody, accessToken, userId);
     * return ResponseEntity.ok("Nickname updated successfully");
     * } catch (Exception e) {
     * return ResponseEntity.badRequest().body("Failed to update nickname");
     * }
     * }
     * 
     * @PutMapping("/auth0/update-picture")
     * public ResponseEntity<String> updatePicture(
     * 
     * @RequestParam("userId") String userId,
     * 
     * @RequestPart("file") MultipartFile file) throws Exception {
     * try {
     * MultipartFile newPicture = file;
     * 
     * String fileUrl = new UploadFile().uploadToS3(newPicture);
     * 
     * String accessToken = getAccessToken();
     * 
     * JsonObject requestBodyJson = new JsonObject();
     * requestBodyJson.addProperty("picture", fileUrl);
     * String requestBody = requestBodyJson.toString();
     * 
     * setUserProperty(requestBody, accessToken, userId);
     * 
     * return ResponseEntity.ok("Nickname updated successfully");
     * 
     * } catch (Exception e) {
     * e.printStackTrace();
     * return ResponseEntity.badRequest().body("Failed to update nickname");
     * }
     * 
     * }
     * 
     * @PutMapping("/auth0/setHasSetUpAccount")
     * public ResponseEntity<String> setHasSetUpAccount(@RequestBody LoginRequest
     * request) {
     * final boolean testing = true;
     * try {
     * if (!testing) {
     * String accessToken = getAccessToken();
     * 
     * JsonObject requestBodyJson = new JsonObject();
     * JsonObject appMetadata = new JsonObject();
     * 
     * appMetadata.addProperty("hasSetUpAccount", true);
     * requestBodyJson.add("app_metadata", appMetadata);
     * 
     * String requestBody = requestBodyJson.toString();
     * 
     * setUserProperty(requestBody, accessToken, request.getUserId());
     * }
     * return ResponseEntity.ok("User has set up account");
     * } catch (Exception e) {
     * e.printStackTrace();
     * return
     * ResponseEntity.badRequest().body("Failed to set has set up account in");
     * }
     * }
     * 
     * @PutMapping("/auth0/setUserGender")
     * public ResponseEntity<String> setUserGender(@RequestBody GenderUpdateRequest
     * request) {
     * try {
     * String accessToken = getAccessToken();
     * 
     * JsonObject requestBodyJson = new JsonObject();
     * JsonObject userMetadata = new JsonObject();
     * 
     * userMetadata.addProperty("gender", request.getGender());
     * requestBodyJson.add("user_metadata", userMetadata);
     * 
     * String requestBody = requestBodyJson.toString();
     * setUserProperty(requestBody, accessToken, request.getUserId());
     * return ResponseEntity.ok("User gender updated successfully");
     * } catch (Exception e) {
     * e.printStackTrace();
     * return ResponseEntity.badRequest().body("Failed to set user gender");
     * }
     * }
     */

    @PatchMapping("/auth0/updateUserInformation")
    public ResponseEntity<String> updateUserInformation(@RequestBody String request) {
        try {
            System.out.println("\n\n" + request + "\n\n"); // Debugging: Check if the request is parsed correctly
            return ResponseEntity.ok(request);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }

    @GetMapping("/auth0/getLoginInformation")
    public ResponseEntity<String> getLoginInformation(@RequestParam("userId") String userId) {
        try {
            String accessToken = getAccessToken();
            String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());
            HttpResponse<String> auth0ApiResponse = Unirest
                    .get("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                    .header("authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("cache-control", "no-cache")
                    .asString();

            JsonObject jsonResponse = JsonParser.parseString(auth0ApiResponse.getBody()).getAsJsonObject();
            int loginAmount = jsonResponse.get("logins_count").getAsInt();
            boolean hasSetUpAccount = jsonResponse.has("app_metadata") &&
                    jsonResponse.get("app_metadata").getAsJsonObject().has("hasSetUpAccount") &&
                    jsonResponse.get("app_metadata").getAsJsonObject().get("hasSetUpAccount").getAsBoolean();

            JsonObject result = new JsonObject();
            result.addProperty("loginAmount", loginAmount);
            result.addProperty("hasSetUpAccount", hasSetUpAccount);

            return ResponseEntity.ok(result.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to get login information");
        }
    }

    RedditClientManager redditClientManager = new RedditClientManager();

    @PostMapping("/reddit/topReddit")
    public ResponseEntity<String> getTopRedditData() throws SQLException {
        try {
            TopRedditData redditData = new TopRedditData();

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
            TopRedditData redditData = new TopRedditData();
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

    private void setUserProperty(String requestBody, String accessToken, String userId) throws Exception {
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

    private CompletableFuture<RedditPost[]> requestDataFromReddit(TopRedditData redditData, String subredditName,
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
