package controller.paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import dataManagement.UserManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.FavoritePostObject;
import structure.TrendyClasses.RedditPost;
import structure.TrendyClasses.RequestEntityForTrend;
import structure.TrendyClasses.TopRedditRequest;
import trendData.redditData.RedditDataFetcher;

class RedditPathTest {
    
    private RedditDataFetcher redditDataFetcher;
    private RedditClient redditClient;
    private UserManager userManager;
    private RedditPath redditPath;
    
    @BeforeEach
    public void setup() {
        // Manual mocking instead of using annotations
        redditDataFetcher = mock(RedditDataFetcher.class);
        redditClient = mock(RedditClient.class);
        userManager = mock(UserManager.class);
        
        // Create RedditPath with a test constructor or use reflection to set fields
        redditPath = new RedditPath();
        
        // Use reflection to set the private fields
        try {
            java.lang.reflect.Field dataFetcherField = RedditPath.class.getDeclaredField("redditDataFetcher");
            dataFetcherField.setAccessible(true);
            dataFetcherField.set(redditPath, redditDataFetcher);
            
            java.lang.reflect.Field clientField = RedditPath.class.getDeclaredField("redditClient");
            clientField.setAccessible(true);
            clientField.set(redditPath, redditClient);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test setup failed: " + e.getMessage());
        }
    }
    
    @Test
    void testGetTopTrendsForCategory() throws Exception {
        // Create test data
        String categoryName = "technology";
        
        RequestEntityForTrend request = mock(RequestEntityForTrend.class);
        when(request.getCategoryName()).thenReturn(categoryName);
        
        RedditPost[] mockPosts = new RedditPost[] {
            new RedditPost("Test Post 1", categoryName, 1, 1000, "Info", "link", "1c4afd2"),
            new RedditPost("Test Post 2", categoryName, 0, 500, "Info", "link", "1c4afd4")
        };
        
        // Configure mock behavior for redditDataFetcher
        when(redditDataFetcher.getData(eq(categoryName), any(RedditClient.class), anyInt())).thenReturn(mockPosts);
        
        // Execute the method
        ResponseEntity<String> response = redditPath.getTopTrendsForCategory(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Test Post 1"));
        assertTrue(response.getBody().contains("Test Post 2"));
        
        // Verify the interaction with the mocked dependencies
        verify(redditDataFetcher).getData(eq(categoryName), any(RedditClient.class), anyInt());
    }
    
    @Test
    void testGetTopRedditData() throws Exception {
        // Create test data
        String userId = "user123";
        int requestAmount = 5;
        
        TopRedditRequest request = mock(TopRedditRequest.class);
        when(request.getUserId()).thenReturn(userId);
        when(request.getRequestAmount()).thenReturn(requestAmount);
        
        ArrayList<FavoritePostObject> favorites = new ArrayList<>();
        favorites.add(new FavoritePostObject("post1", "technology", "2023-05-20"));
        
        // Configure UserManager mock
        when(userManager.getUsersFavoritePostsIds(eq(userId))).thenReturn(favorites);
        
        // Create mock posts equal to requestAmount
        RedditPost[] mockPosts = new RedditPost[requestAmount];
        for (int i = 0; i < requestAmount; i++) {
            mockPosts[i] = new RedditPost(
                "Tech Post " + i,
                "technology",
                1,
                1000,
                "Info",
                "link",
                "tech" + i
            );
        }
        
        // Mock the CompletableFuture that would be returned by the private method
        CompletableFuture<RedditPost[]> futurePosts = CompletableFuture.completedFuture(mockPosts);
        
        // Create a spy of the redditPath to stub the private method
        RedditPath spyRedditPath = spy(redditPath);
        
        // Use doReturn for the private method
        doReturn(futurePosts).when(spyRedditPath).requestDataFromReddit(
                any(RedditDataFetcher.class), anyString(), any(RedditClient.class), eq(requestAmount));
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyRedditPath.getTopRedditData(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Tech Post"));
    }
}
