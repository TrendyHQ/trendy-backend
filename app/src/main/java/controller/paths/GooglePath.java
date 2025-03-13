package controller.paths;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.cdimascio.dotenv.Dotenv;
import trendData.googleTrendsData.GoogleManager;

@RestController
@RequestMapping("/api/google")
public class GooglePath {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    @GetMapping("/info")
    public ResponseEntity<String> getGoogleInfo(
            @RequestParam(required = true) String location) {

        try {
            JsonObject currentGoogleData = getCurrentGoogleData(location);
            if (currentGoogleData != null) {
                // Return current data immediately and update in the background
                if (currentGoogleData.has("updated_at")) {
                    String lastUpdated = currentGoogleData.get("updated_at").getAsString();
                    // Check when this data was last updated
                    LocalDateTime lastUpdatedTime = LocalDateTime.parse(lastUpdated.replace(" ", "T"));
                    LocalDateTime now = LocalDateTime.now();

                    // Only update data if it’s more than 1 hour old
                    if (lastUpdatedTime.plusHours(1).isBefore(now)) {
                        new Thread(() -> {
                            try {
                                String newData = updateData(location);
                                setCurrentGoogleData(location, newData);
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                }

                return ResponseEntity.ok(currentGoogleData.toString());
            }

            // If no data exists yet, update and return the new data
            String jsonData = updateData(location);
            return ResponseEntity.ok(jsonData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to receive data");
        }
    }

    private JsonObject getCurrentGoogleData(String location) throws SQLException {
        // Method that checks the sql database to see if there is stored information
        // about the 12 categories and returns it

        String locationCode;
        try {
            locationCode = new GoogleManager().getLocationCode(location);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String postInsertQuery = "SELECT json_data, updated_at FROM google_data WHERE location_code = ?";
            try (PreparedStatement postStmt = connection.prepareStatement(postInsertQuery)) {
                postStmt.setString(1, locationCode); // Use the actual location code
                ResultSet rs = postStmt.executeQuery();
                if (rs.next()) {
                    String jsonData = rs.getString("json_data");
                    String timeStamp = rs.getString("updated_at");

                    JsonObject result = JsonParser.parseString(jsonData).getAsJsonObject();
                    result.addProperty("updated_at", timeStamp);
                    return result;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return new JsonObject();
            }
        }

        return null;
    }

    private void setCurrentGoogleData(String location, String jsonData) throws SQLException {
        // Method that sets the sql database with the 12 categories and their data
        String locationCode;
        try {
            locationCode = new GoogleManager().getLocationCode(location);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String postInsertQuery = "INSERT INTO google_data (location_code, json_data) VALUES (?, ?) ON DUPLICATE KEY UPDATE json_data = ?";
            try (PreparedStatement postStmt = connection.prepareStatement(postInsertQuery)) {
                postStmt.setString(1, locationCode); // Use the actual location code
                postStmt.setString(2, jsonData);
                postStmt.setString(3, jsonData);
                postStmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String updateData(String location) throws SQLException {
        GoogleManager googleManager = new GoogleManager();
        JsonObject response1 = googleManager.fetchInfo("cats", location);
        JsonObject response2 = googleManager.fetchInfo("dogs", location);
        // JsonObject response3 = googleManager.fetchInfo("", location);
        // JsonObject response4 = googleManager.fetchInfo("", location);
        // JsonObject response5 = googleManager.fetchInfo("", location);
        // JsonObject response6 = googleManager.fetchInfo("", location);
        // JsonObject response7 = googleManager.fetchInfo("", location);
        // JsonObject response8 = googleManager.fetchInfo("", location);
        // JsonObject response9 = googleManager.fetchInfo("", location);
        // JsonObject response10 = googleManager.fetchInfo("", location);
        // JsonObject response11 = googleManager.fetchInfo("", location);
        // JsonObject response12 = googleManager.fetchInfo("", location);

        // Create a JSON object to hold all responses
        JsonObject responseData = new JsonObject();
        responseData.add("cats", response1);
        responseData.add("dogs", response2);
        // When you uncomment the other responses, add them to the jsonData object like:
        // responseData.add("category3", response3);
        // responseData.add("category4", response4);
        // etc.

        String jsonData = responseData.toString();

        // Set the data in the database
        setCurrentGoogleData(location, jsonData);

        return jsonData;
    }
}
