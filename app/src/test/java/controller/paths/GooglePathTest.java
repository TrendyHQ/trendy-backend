package controller.paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import trendData.googleTrendsData.GoogleManager;

class GooglePathTest {

    @Mock
    private GoogleManager googleManager;
    
    @InjectMocks
    private GooglePath googlePath;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void testGetGoogleInfoWithExistingData() throws Exception {
        // Simplify the test by mocking the response directly
        String location = "47.6062,-122.3321";
        
        // Create test JSON data
        JsonArray mockDataArray = new JsonArray();
        JsonObject testObj = new JsonObject();
        testObj.addProperty("title", "Technology");
        testObj.addProperty("isTrending", true);
        mockDataArray.add(testObj);
        
        // First mock the GoogleManager to setup the test
        when(googleManager.getLocationCode(anyString())).thenReturn("US-WA");
        when(googleManager.fetchInfo(anyString(), anyString())).thenReturn(testObj);
        
        // Create a spy to intercept the method call
        GooglePath spyPath = spy(googlePath);
        
        // Mock the private method call to avoid database interaction
        doReturn(mockDataArray).when(spyPath).getCurrentGoogleData(anyString());
        doNothing().when(spyPath).setCurrentGoogleData(anyString(), anyString());
        
        // Execute the method
        ResponseEntity<String> response = spyPath.getGoogleInfo(location);
        
        // Verify the result
        assertEquals(200, response.getStatusCode().value());
        
        // Note: Since we're mocking getCurrentGoogleData, the actual content will be from that mock
        assertNotNull(response.getBody());
    }
}
