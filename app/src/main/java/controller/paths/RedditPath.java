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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;

import dataManagement.UserManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.FavoritePostObject;
import structure.TrendyClasses.RedditPost;
import structure.TrendyClasses.TopRedditRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

@RestController
@RequestMapping("/api/reddit") // Define the base URL for your endpoints
public class RedditPath {
    RedditClientManager redditClientManager = new RedditClientManager();

    @PostMapping("/topReddit")
    public ResponseEntity<String> getTopRedditData(@RequestBody TopRedditRequest request) throws SQLException {
        try {
            if (redditClientManager.getClient() == null) {
                redditClientManager.autherizeClient();
            }

            RedditClient redditClient = redditClientManager.getClient();

            int amount = request.getRequestAmount();
            RedditDataFetcher redditData = new RedditDataFetcher();

            String[] subreddits = { "fashion", "technology", "food", "entertainment", "socialmedia",
                    "fitness", "wellness", "music", "politics", "travel", "science", "sports" };

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
            int totalToAllocate = amount;

            // Minimum posts per category to ensure diversity
            int minPostsPerCategory = Math.round(totalToAllocate / subreddits.length);
            int reservedPosts = minPostsPerCategory * subreddits.length / 2;
            int remainingToAllocate = Math.max(0, totalToAllocate - reservedPosts);

            // Request data from each subreddit with proportional limits
            for (String subreddit : subreddits) {
                // Calculate limit proportionally to favorites, with a minimum
                if (categoryCounts.get(subreddit) + 5 < Collections.max(categoryCounts.values())) {
                    if (Math.random() > 0.5) {
                        continue;
                    } else {
                        limitPerSubreddit = minPostsPerCategory;
                    }
                } else if ((totalFavorites > 0 && remainingToAllocate > 0) || categoryCounts.get(subreddit) > 0) {
                    double proportion = (double) categoryCounts.get(subreddit) / totalFavorites;
                    // Set minimum to 2 for categories with favorites
                    int categoryMin = categoryCounts.get(subreddit) > 0 ? minPostsPerCategory * 2 : minPostsPerCategory;
                    limitPerSubreddit = categoryMin + (int) Math.round(proportion * remainingToAllocate);
                } else {
                    limitPerSubreddit = minPostsPerCategory;
                }

                futures.add(requestDataFromReddit(redditData, subreddit, redditClient, limitPerSubreddit));
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
                double p1WeightedScore = p1.getScore() * (1 + (p1CategoryCount * 0.5));
                double p2WeightedScore = p2.getScore() * (1 + (p2CategoryCount * 0.5));

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
                        RedditPost[] topCategoryPosts = redditData.getData(topFavoriteCategory, redditClient, 1);
                        if (topCategoryPosts != null && topCategoryPosts.length > 0) {
                            allPosts.add(topCategoryPosts[0]);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Take top 'amount' posts
            RedditPost[] topPosts = allPosts.stream().limit(amount).toArray(RedditPost[]::new);

            return ResponseEntity.ok(new Gson().toJson(topPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }

    @PostMapping("/topTrendsForCategory")
    public ResponseEntity<String> getTopTrendsForCategory(@RequestBody String entity) {
        try {
            if (redditClientManager.getClient() == null) {
                redditClientManager.autherizeClient();
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

    private CompletableFuture<RedditPost[]> requestDataFromReddit(RedditDataFetcher redditData, String subredditName,
            RedditClient redditClient, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int limit = Math.round(amount / 3);
                return redditData.getData(subredditName, redditClient, limit);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }
}
