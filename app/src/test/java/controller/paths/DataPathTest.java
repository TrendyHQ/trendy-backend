package controller.paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import dataManagement.FeedbackManager;
import dataManagement.StorageManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.*;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

class DataPathTest {
    
    private static final Logger logger = LoggerFactory.getLogger(DataPathTest.class);

    @Mock
    private RedditClientManager redditClientManager;
    
    @Mock
    private RedditClient redditClient;
    
    @Mock
    private RedditDataFetcher redditDataFetcher;
    
    @Mock
    private StorageManager storageManager;
    
    @Mock
    private FeedbackManager feedbackManager;
    
    @InjectMocks
    private DataPath dataPath;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Setup common mock behavior
        new RedditClientManager().authorizeClient();
        redditClient = redditClientManager.getClient();
    }
    
    @Test
    void testGetSpecificTrendData() throws Exception {
        // Prepare test data
        String postId = "1k6bl20";
        String userId = "testUserId";
        
        SpecificPost specificPost = new SpecificPost("Test Title", 1000, "More info", "link", postId, "technology");
        PostInfoObject postInfo = new PostInfoObject(50, new CommentObject[0], false, false);
        
        // Use a spy of dataPath
        DataPath spyDataPath = spy(dataPath);
        
        // Configure mock behavior with generic matchers
        when(redditDataFetcher.getSpecificPost(anyString(), any(), anyBoolean())).thenReturn(specificPost);
        when(storageManager.getInformationOnPost(anyString(), anyString())).thenReturn(postInfo);
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyDataPath.getSpecificTrendData(postId, userId);

        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"title\":"));
        assertTrue(response.getBody().contains("\"otherInformation\":"));
    }
    
    @Test
    void testAddCommentToPost() throws Exception {
        // Create simple mock objects
        CommentObject comment = mock(CommentObject.class);
        when(comment.getUserId()).thenReturn("user1");
        when(comment.getValue()).thenReturn("Test comment");
        when(comment.getDatePublished()).thenReturn("2025-4-24");
        when(comment.getNick()).thenReturn("tester");
        when(comment.getAvatar()).thenReturn("avatar.jpg");
        
        CommentRequest request = mock(CommentRequest.class);
        when(request.getPostId()).thenReturn("1c4afd2");
        when(request.getComment()).thenReturn(comment);
        
        // Use a spy of dataPath
        DataPath spyDataPath = spy(dataPath);
        
        // Use doNothing for void methods
        doNothing().when(storageManager).putCommentOnPost(anyString(), any(CommentObject.class));
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyDataPath.addCommentToPost(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Comment added successfully", response.getBody());
    }
    
    @Test
    void testSetLikesOnPost() throws Exception {
        // Create a proper mock implementation
        LikeRequest request = mock(LikeRequest.class);
        when(request.getPostId()).thenReturn("1k6bl20");
        when(request.getUserId()).thenReturn("user1");
        when(request.getLike()).thenReturn(1);
        
        // Use a spy of dataPath
        DataPath spyDataPath = spy(dataPath);
        
        // Configure StorageManager mock with generic matchers
        when(storageManager.setLikesOnPost(anyString(), anyString(), anyInt())).thenReturn(42);
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyDataPath.setLikesOnPost(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"status\":\"success\""));
    }
}
