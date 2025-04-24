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
        // Simplify the test with generic mocking approach
        String location = "35.776102,-78.885683";
        
        // Create mock response data
        JsonArray mockDataArray = new JsonArray();
        JsonObject testObj = new JsonObject();
        testObj.addProperty("title", "Technology");
        testObj.addProperty("isTrending", true);
        mockDataArray.add(testObj);

        // timestamp in ISO‑8601 format without zone (matches LocalDateTime.parse after replace)
        mockDataArray.add("2025-04-24T00:00:00");

        // Create a spy of googlePath
        GooglePath spyGooglePath = spy(googlePath);

        // Mock the location code lookup
        when(googleManager.getLocationCode(anyString())).thenReturn("US");
        
        // Mock the private method calls
        doReturn(mockDataArray).when(spyGooglePath).getCurrentGoogleData(anyString());
        doNothing().when(spyGooglePath).setCurrentGoogleData(anyString(), anyString());
        
        // Mock the Google API call in case it's needed
        when(googleManager.fetchInfo(anyString(), anyString())).thenReturn(testObj);
        
        // Execute the method using the spy
        ResponseEntity<String> response = spyGooglePath.getGoogleInfo(location);
        
        // Basic verification
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}
