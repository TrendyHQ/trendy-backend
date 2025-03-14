package controller.paths;

import java.sql.SQLException;

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
import structure.TrendyClasses.CommentRequest;
import structure.TrendyClasses.FeedbackObject;
import structure.TrendyClasses.FeedbackRequest;
import structure.TrendyClasses.LikeRequest;
import structure.TrendyClasses.PostData;
import structure.TrendyClasses.SpecificPost;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

@RestController
@RequestMapping("/api/data") // Define the base URL for your endpoints
public class DataPath {
    RedditClientManager redditClientManager = new RedditClientManager();

    @GetMapping("/trend")
    public ResponseEntity<String> getSpecificTrendData(@RequestParam String postId, @RequestParam String userId)
            throws SQLException {
        RedditDataFetcher redditData = new RedditDataFetcher();
        try {
            if (redditClientManager.getClient() == null) {
                redditClientManager.authorizeClient();
            }

            RedditClient redditClient = redditClientManager.getClient();

            SpecificPost post = redditData.getSpecificPost(postId, redditClient, true);
            PostData postData = new PostData(post.getTitle(), post.getScore(), post.getMoreInfo(), post.getLink(),
                    post.getId(), post.getCategory(), new StorageManager().getInformationOnPost(postId, userId));

            return ResponseEntity.ok(new Gson().toJson(postData));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.badRequest().body("Failed to receive data");
    }

    @PutMapping("/addCommentToPost")
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

    @PutMapping("/setLikesOnPost")
    public ResponseEntity<String> setLikesOnPost(@RequestBody LikeRequest request) {
        try {
            String userId = request.getUserId();
            String postId = request.getPostId();
            int isLike = request.getLike(); // 1 for like, 0 for neutral, -1 for dislike
            StorageManager storageManager = new StorageManager();
            int updatedLikes = storageManager.setLikesOnPost(userId, postId, isLike);

            return ResponseEntity.ok("{\"status\":\"success\",\"likes\":" + updatedLikes + "}");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok("{\"status\":\"error\",\"message\":\"" + "Error setting likes on post" + "\"}");
        }
    }

    @PutMapping("/addFeedbackToDatabase")
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
}
