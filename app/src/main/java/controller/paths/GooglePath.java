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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
            @RequestParam(name = "location", required = true) String location) {

        try {
            JsonArray currentGoogleData = getCurrentGoogleData(location);
            if (currentGoogleData != null) {
                // Return current data immediately and update in the background
                if (currentGoogleData.get(currentGoogleData.size() - 1).getAsString() != null) {
                    String lastUpdated = currentGoogleData.get(currentGoogleData.size() - 1).getAsString();
                    // Check when this data was last updated
                    LocalDateTime lastUpdatedTime = LocalDateTime.parse(lastUpdated.replace(" ", "T"));
                    LocalDateTime now = LocalDateTime.now();

                    // Only update data if it’s more than 1 day old or if the data size is not 13
                    if (lastUpdatedTime.plusDays(1).isBefore(now) || currentGoogleData.size() != 13 && false) {
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

    /**
     * Retrieves current Google Trends data for a specific location from the
     * database.
     * 
     * @param location The geographical location to get trend data for
     * @return JsonArray containing trend data and timestamp, or null if no data
     *         exists
     * @throws SQLException If a database access error occurs
     */
    public JsonArray getCurrentGoogleData(String location) throws SQLException {
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

                    JsonArray result = JsonParser.parseString(jsonData).getAsJsonArray();
                    result.add(timeStamp);
                    return result;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return new JsonArray();
            }
        }

        return null;
    }

    /**
     * Saves Google Trends data for a specific location to the database.
     * Converts the location to a location code and updates or inserts the JSON
     * data.
     * Only processes the data if the JSON array contains more than one element.
     *
     * @param location The geographical location to save trend data for
     * @param jsonData The trend data in JSON string format
     * @throws SQLException If a database access error occurs
     */
    public void setCurrentGoogleData(String location, String jsonData) throws SQLException {
        // Method that sets the sql database with the 12 categories and their data
        JsonArray jsonArray = JsonParser.parseString(jsonData).getAsJsonArray();
        if (jsonArray.size() == 12) {
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
    }

    private String updateData(String location) throws SQLException {
        GoogleManager googleManager = new GoogleManager();

        String[] searchQueries = {
                "Fashion", "Technology", "Food", "Entertainment",
                "Media", "Fitness", "Health", "Music",
                "Politics", "Travel", "Science", "Sports"
        };

        // Create a JSON object to hold all responses
        JsonArray responseData = new JsonArray();

        try {
            for (String category : searchQueries) {
                JsonObject response = googleManager.fetchInfo(category, location);
                response.addProperty("title", category);
                // convert JsonObject to JsonElement
                JsonElement responseElement = JsonParser.parseString(response.toString());
                responseData.add(responseElement);
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String jsonData = responseData.toString();

        // Set the data in the database
        setCurrentGoogleData(location, jsonData);

        return jsonData;
    }
}
