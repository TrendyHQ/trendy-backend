package controller.paths;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;

import dataManagement.FeedbackManager;
import dataManagement.StorageManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.*;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

@RestController
@RequestMapping("/api/data") // Define the base URL for your endpoints
public class DataPath {
    private static final Logger LOGGER = Logger.getLogger(DataPath.class.getName());
    private final RedditClientManager redditClientManager = new RedditClientManager();
    private final FeedbackManager feedbackManager = new FeedbackManager();

    /**
     * Retrieves data for a specific trend based on the post ID and user ID.
     * 
     * @param postId The unique identifier of the post to retrieve
     * @param userId The ID of the user requesting the trend data
     * @return ResponseEntity containing JSON data of the specific post or an error message
     * @throws SQLException If a database access error occurs
     */
    @GetMapping("/trend")
    public ResponseEntity<String> getSpecificTrendData(@RequestParam String postId, @RequestParam String userId)
            throws SQLException {
        // Validate input parameters
        if (postId == null || postId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Post ID cannot be empty\"}");
        }
        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"User ID cannot be empty\"}");
        }

        RedditDataFetcher redditData = new RedditDataFetcher();
        
        try {
            if (redditClientManager.getClient() == null) {
                redditClientManager.authorizeClient();
            }

            RedditClient redditClient = redditClientManager.getClient();
            if (redditClient == null) {
                LOGGER.log(Level.SEVERE, "Failed to authorize Reddit client");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to connect to Reddit API\"}");
            }

            SpecificPost post = redditData.getSpecificPost(postId, redditClient, true);
            if (post == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"Post not found\"}");
            }

            PostData postData = new PostData(post.getTitle(), post.getScore(), post.getMoreInfo(), post.getLink(),
                    post.getId(), post.getCategory(), new StorageManager().getInformationOnPost(postId, userId));

            return ResponseEntity.ok(new Gson().toJson(postData));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error fetching trend data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Failed to receive data: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Adds a comment to a specific post.
     * 
     * @param request CommentRequest object containing postId and comment text
     * @return ResponseEntity with success message or error message
     */
    @PutMapping("/addCommentToPost")
    public ResponseEntity<String> addCommentToPost(@RequestBody CommentRequest request) {
        // Validate request
        if (request == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"Request body cannot be null\"}");
        }
        if (request.getPostId() == null || request.getPostId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Post ID cannot be empty\"}");
        }
        if (request.getComment() == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"Comment cannot be null\"}");
        }

        try {
            StorageManager storageManager = new StorageManager();
            storageManager.putCommentOnPost(request.getPostId(), request.getComment());

            return ResponseEntity.ok("Comment added successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error adding comment to post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Failed to add comment: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Sets a like/dislike status for a post by a specific user.
     * 
     * @param request LikeRequest object containing userId, postId, and like value
     * @return ResponseEntity with JSON containing updated like count or error message
     */
    @PutMapping("/setLikesOnPost")
    public ResponseEntity<String> setLikesOnPost(@RequestBody LikeRequest request) {
        // Validate request
        if (request == null) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"Request body cannot be null\"}");
        }
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"User ID cannot be empty\"}");
        }
        if (request.getPostId() == null || request.getPostId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"Post ID cannot be empty\"}");
        }
        if (request.getLike() < -1 || request.getLike() > 1) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"Like value must be -1, 0, or 1\"}");
        }

        try {
            String userId = request.getUserId();
            String postId = request.getPostId();
            int isLike = request.getLike(); // 1 for like, 0 for neutral, -1 for dislike
            StorageManager storageManager = new StorageManager();
            int updatedLikes = storageManager.setLikesOnPost(userId, postId, isLike);

            return ResponseEntity.ok("{\"status\":\"success\",\"likes\":" + updatedLikes + "}");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting likes on post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"status\":\"error\",\"message\":\"Error setting likes on post: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Adds user feedback or report to the database.
     * 
     * @param feedback FeedbackRequest object containing userId, feedback text, and isReport flag
     * @return ResponseEntity with success message or error message
     */
    @PutMapping("/addFeedbackToDatabase")
    public ResponseEntity<String> addFeedbackToDatabase(@RequestBody FeedbackRequest feedback) {
        // Validate request
        if (feedback == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"Request body cannot be null\"}");
        }
        if (feedback.getUserId() == null || feedback.getUserId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"User ID cannot be empty\"}");
        }
        if (feedback.getFeedback() == null || feedback.getFeedback().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"Feedback cannot be empty\"}");
        }

        try {
            feedbackManager.addFeedbackToDatabase(new FeedbackObject(feedback.getUserId(), feedback.getFeedback()),
                    feedback.getIsReport());

            return ResponseEntity.ok("Feedback added successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error adding feedback to database", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\":\"Failed to add feedback: " + e.getMessage() + "\"}");
        }
    }
}
