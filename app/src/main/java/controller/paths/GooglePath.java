package controller.paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;

import trendData.googleTrendsData.GoogleManager;

@RestController
@RequestMapping("/api/google")
public class GooglePath {
    @GetMapping("/info")
    public ResponseEntity<String> getGoogleInfo(
            @RequestParam(required = true) Object query,
            @RequestParam(required = true) String location) {

        try {
            GoogleManager googleManager = new GoogleManager();
            JsonObject response = googleManager.fetchInfo(query, location);
            return ResponseEntity.ok(response.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }
}
