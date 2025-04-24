package controller.paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import com.google.gson.Gson;

import dataManagement.UserManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.FavoritePostObject;
import structure.TrendyClasses.RedditPost;
import structure.TrendyClasses.RequestEntityForTrend;
import structure.TrendyClasses.TopRedditRequest;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

class RedditPathTest {

    @Mock
    private RedditClientManager redditClientManager;
    
    @Mock
    private RedditClient redditClient;
    
    @Mock
    private RedditDataFetcher redditDataFetcher;
    
    @Mock
    private UserManager userManager;
    
    @InjectMocks
    private RedditPath redditPath;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(redditClientManager.getClient()).thenReturn(redditClient);
    }
    
    @Test
    void testGetTopTrendsForCategory() throws Exception {
        // Create test data
        String categoryName = "technology";
        
        RequestEntityForTrend request = mock(RequestEntityForTrend.class);
        when(request.getCategoryName()).thenReturn(categoryName);
        
        RedditPost[] mockPosts = new RedditPost[] {
            new RedditPost("Test Post 1", categoryName, 1, 1000, "Info", "link", "id1"),
            new RedditPost("Test Post 2", categoryName, 0, 500, "Info", "link", "id2")
        };
        
        // Configure mock behavior for redditDataFetcher
        when(redditDataFetcher.getData(anyString(), any(), anyInt())).thenReturn(mockPosts);
        
        // Execute the method
        ResponseEntity<String> response = redditPath.getTopTrendsForCategory(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Test Post 1"));
        assertTrue(response.getBody().contains("Test Post 2"));
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
        when(userManager.getUsersFavoritePostsIds(anyString())).thenReturn(favorites);
        
        // Create mock posts
        RedditPost[] mockPosts = new RedditPost[] {
            new RedditPost("Tech Post", "technology", 1, 1000, "Info", "link", "tech1")
        };
        
        // Mock the CompletableFuture that would be returned by the private method
        CompletableFuture<RedditPost[]> futurePosts = CompletableFuture.completedFuture(mockPosts);
        
        // Create a spy of the redditPath to stub the private method
        RedditPath spyRedditPath = spy(redditPath);
        
        // Use doReturn for the private method
        doReturn(futurePosts).when(spyRedditPath).requestDataFromReddit(
                any(RedditDataFetcher.class), anyString(), any(RedditClient.class), anyInt());
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyRedditPath.getTopRedditData(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Tech Post"));
    }
}
