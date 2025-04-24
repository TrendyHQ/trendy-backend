package controller.paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import dataManagement.FeedbackManager;
import dataManagement.StorageManager;
import net.dean.jraw.RedditClient;
import structure.TrendyClasses.*;
import trendData.redditData.RedditClientManager;
import trendData.redditData.RedditDataFetcher;

class DataPathTest {

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
        when(redditClientManager.getClient()).thenReturn(redditClient);
    }
    
    @Test
    void testGetSpecificTrendData() throws Exception {
        // Prepare test data
        String postId = "testPostId";
        String userId = "testUserId";
        
        SpecificPost specificPost = new SpecificPost("Test Title", 1000, "More info", "link", postId, "technology");
        PostInfoObject postInfo = new PostInfoObject(50, new CommentObject[0], false, false);
        
        // Configure mock behavior - use anyX() matchers to avoid IllegalArgumentException
        when(redditDataFetcher.getSpecificPost(anyString(), any(), anyBoolean())).thenReturn(specificPost);
        when(storageManager.getInformationOnPost(anyString(), anyString())).thenReturn(postInfo);
        
        // Execute the method
        ResponseEntity<String> response = dataPath.getSpecificTrendData(postId, userId);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("Test Title"));
        assertTrue(response.getBody().contains("technology"));
    }
    
    @Test
    void testAddCommentToPost() throws Exception {
        // Create simple mock objects instead of anonymous implementations
        CommentObject comment = mock(CommentObject.class);
        when(comment.getUserId()).thenReturn("user1");
        when(comment.getValue()).thenReturn("Test comment");
        when(comment.getDatePublished()).thenReturn("2023-05-20");
        when(comment.getNick()).thenReturn("tester");
        when(comment.getAvatar()).thenReturn("avatar.jpg");
        
        CommentRequest request = mock(CommentRequest.class);
        when(request.getPostId()).thenReturn("post123");
        when(request.getComment()).thenReturn(comment);
        
        // Don't need to configure StorageManager behavior since it's void
        
        // Execute the method
        ResponseEntity<String> response = dataPath.addCommentToPost(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertEquals("Comment added successfully", response.getBody());
        
        // Verify the service was called
        verify(storageManager).putCommentOnPost(anyString(), any(CommentObject.class));
    }
    
    @Test
    void testSetLikesOnPost() throws Exception {
        // Create a proper mock implementation
        LikeRequest request = mock(LikeRequest.class);
        when(request.getPostId()).thenReturn("post123");
        when(request.getUserId()).thenReturn("user1");
        when(request.getLike()).thenReturn(1);
        
        // Configure StorageManager mock
        when(storageManager.setLikesOnPost(anyString(), anyString(), anyInt())).thenReturn(42);
        
        // Execute the method
        ResponseEntity<String> response = dataPath.setLikesOnPost(request);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"likes\":42"));
        
        // Verify the expected method call
        verify(storageManager).setLikesOnPost(anyString(), anyString(), anyInt());
    }
}
