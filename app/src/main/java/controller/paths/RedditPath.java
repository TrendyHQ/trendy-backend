package controller.paths;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import dataManagement.UserManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.FavoritePostObject;
import structure.TrendyClasses.RedditPost;
import structure.TrendyClasses.RequestEntityForTrend;
import structure.TrendyClasses.TopRedditRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

@RestController
@RequestMapping("/api/reddit")
public class RedditPath {
    
    private RedditDataFetcher redditDataFetcher;
    private RedditClient redditClient;
    
    public RedditPath() {
        this.redditDataFetcher = new RedditDataFetcher();
        try {
            RedditClientManager redditClientManager = new RedditClientManager();
            redditClientManager.authorizeClient();
            this.redditClient = redditClientManager.getClient();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize Reddit client: " + e.getMessage());
        }
    }

    /**
     * Retrieves top trends for a specific category/entity from Reddit.
     * 
     * @param entity The category or entity name to fetch trends for
     * @return ResponseEntity with JSON array of posts from the specified category or error message
     */
    @PostMapping("/topTrendsForCategory")
    public ResponseEntity<String> getTopTrendsForCategory(@RequestBody String entity) {
        RedditClientManager redditClientManager = new RedditClientManager();

        try {
            if (redditClientManager.getClient() == null) {
                redditClientManager.authorizeClient();
            }

            RedditClient redditClient = redditClientManager.getClient();

            int limit = 30;
            RedditDataFetcher redditData = new RedditDataFetcher();
            RedditPost[] posts = redditData.getData(entity, redditClient, limit);

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
    
    /**
     * Retrieves top Reddit data based on user preferences and post popularity.
     * Uses user's favorite posts to personalize the content selection.
     * 
     * @param request TopRedditRequest object containing userId and requestAmount
     * @return ResponseEntity with JSON array of personalized Reddit posts or error
     *         message
     * @throws SQLException If a database access error occurs
     */
    @PostMapping("/topReddit")
    public ResponseEntity<String> getTopRedditData(@RequestBody TopRedditRequest request) throws SQLException {
        try {
            if (redditClient == null) {
                RedditClientManager redditClientManager = new RedditClientManager();
                redditClientManager.authorizeClient();
                redditClient = redditClientManager.getClient();
            }

            int amount = request.getRequestAmount();
            
            String[] subreddits = { "fashion", "technology", "food", "entertainment", "socialmedia",
                    "fitness", "health", "music", "politics", "travel", "science", "sports" };

            // Map subreddit names to their request futures
            List<CompletableFuture<RedditPost[]>> futures = new ArrayList<>();
            int limitPerSubreddit;
            String userId = request.getUserId();
            // Get user's favorite posts
            ArrayList<FavoritePostObject> usersFavorites = new UserManager().getUsersFavoritePostsIds(userId);

            // Count posts by category
            Map<String, Integer> categoryCounts = new HashMap<>();
            // Initialize all subreddits with 0 to ensure all categories are included
            for (String subreddit : subreddits) {
                categoryCounts.put(subreddit, 0);
            }

            // Count user's favorites by category
            for (FavoritePostObject favorite : usersFavorites) {
                String category = favorite.getPostCategory();
                categoryCounts.put(category, categoryCounts.getOrDefault(category, 0) + 1);
            }

            // Calculate total favorites and prepare for proportional allocation
            int totalFavorites = categoryCounts.values().stream().mapToInt(Integer::intValue).sum();

            // Request more posts than needed to ensure we have enough after filtering
            int requestBuffer = Math.max(20, amount);
            int totalToAllocate = amount + requestBuffer;

            // Request data from each subreddit with proportional limits - never skip
            // subreddits
            for (String subreddit : subreddits) {
                // Calculate proportional limit based on user preferences
                if (totalFavorites > 0) {
                    double proportion = (double) categoryCounts.getOrDefault(subreddit, 0) / totalFavorites;
                    // Ensure at least some minimum posts from each category
                    limitPerSubreddit = 5 + (int) Math.round(proportion * totalToAllocate);
                } else {
                    // Equal distribution if no favorites
                    limitPerSubreddit = totalToAllocate / subreddits.length;
                }

                // Ensure minimum value
                limitPerSubreddit = Math.max(5, limitPerSubreddit);

                futures.add(requestDataFromReddit(redditDataFetcher, subreddit, redditClient, limitPerSubreddit));
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

            // Remove duplicates by post id, keeping the post with the highest score if
            // duplicates exist
            Map<String, RedditPost> uniquePosts = new LinkedHashMap<>();
            for (RedditPost post : allPosts) {
                String postId = post.getId();
                if (!uniquePosts.containsKey(postId) || post.getScore() > uniquePosts.get(postId).getScore()) {
                    uniquePosts.put(postId, post);
                }
            }
            allPosts = new ArrayList<>(uniquePosts.values());

            // Sort posts by score in descending order
            allPosts.sort((p1, p2) -> {
                // Get category counts for each post's category
                int p1CategoryCount = categoryCounts.getOrDefault(p1.getCategory(), 0);
                int p2CategoryCount = categoryCounts.getOrDefault(p2.getCategory(), 0);

                // Calculate weighted scores that include category preference
                double p1WeightedScore = p1.getScore() * (1 + (p1CategoryCount * 0.3));
                double p2WeightedScore = p2.getScore() * (1 + (p2CategoryCount * 0.3));

                // Sort by weighted score
                return Double.compare(p2WeightedScore, p1WeightedScore);
            });

            // Find the user's top favorite category
            String topFavoriteCategory = null;
            int maxFavorites = -1;

            for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
                if (entry.getValue() > maxFavorites) {
                    maxFavorites = entry.getValue();
                    topFavoriteCategory = entry.getKey();
                }
            }

            // Check if top favorite category has a significant margin (5+ favorites)
            if (maxFavorites > 5) {
                // Create a final copy of topFavoriteCategory for use in lambda
                final String finalTopFavoriteCategory = topFavoriteCategory;

                // Check if this category is represented in the top trends
                boolean topCategoryPresent = allPosts.stream()
                        .limit(amount)
                        .anyMatch(post -> post.getCategory().equals(finalTopFavoriteCategory));

                // If not present, add the most popular post from that category
                if (!topCategoryPresent) {
                    try {
                        RedditPost[] topCategoryPosts = redditDataFetcher.getData(topFavoriteCategory, redditClient, 1);
                        if (topCategoryPosts != null && topCategoryPosts.length > 0) {
                            allPosts.add(topCategoryPosts[0]);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Take exactly the requested number of posts
            RedditPost[] topPosts = allPosts.stream().limit(amount).toArray(RedditPost[]::new);

            // If we still don't have enough posts, log an error
            if (topPosts.length < amount) {
                System.err.println("Warning: Requested " + amount + " posts but only returned " + topPosts.length);
            }

            return ResponseEntity.ok(new Gson().toJson(topPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }

    /**
     * Retrieves top trends for a specific category/entity from Reddit.
     * 
     * @param entity The category or entity name to fetch trends for
     * @return ResponseEntity with JSON array of posts from the specified category
     *         or error message
     */
    @PostMapping("/category")
    public ResponseEntity<String> getTopTrendsForCategory(@RequestBody RequestEntityForTrend request) {
        try {
            if (request == null || request.getCategoryName() == null || request.getCategoryName().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request: category name is required");
            }
            
            String categoryName = request.getCategoryName();
            
            // Get data from Reddit for the specified category
            RedditPost[] posts = redditDataFetcher.getData(categoryName, redditClient, 10); // Default to 10 posts
            
            if (posts == null || posts.length == 0) {
                return ResponseEntity.status(HttpStatus.OK).body("[]");
            }
            
            // Convert to JSON string
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonResponse = objectMapper.writeValueAsString(posts);
            
            return ResponseEntity.status(HttpStatus.OK).body(jsonResponse);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching Reddit trends");
        }
    }

    /**
     * Asynchronously requests Reddit data for a specific subreddit.
     * 
     * @param redditData    The RedditDataFetcher instance to use for fetching data
     * @param subredditName The name of the subreddit to fetch data from
     * @param redditClient  The authenticated RedditClient instance
     * @param amount        The number of posts to request from the subreddit
     * @return CompletableFuture that will contain the array of RedditPost objects
     *         when completed
     */
    public CompletableFuture<RedditPost[]> requestDataFromReddit(RedditDataFetcher redditData, String subredditName,
            RedditClient redditClient, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Request the full amount - remove the division by 3
                return redditData.getData(subredditName, redditClient, amount);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
}
