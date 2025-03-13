package trendData.googleTrendsData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;

public class GoogleManager {
    private JsonObject locationsData;

    public GoogleManager() {
        loadLocationsData();
    }

    private void loadLocationsData() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("google-trends-locations.json");
            if (is != null) {
                locationsData = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            } else {
                throw new RuntimeException("Could not load google-trends-locations.json");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getLocationCode(String location) throws Exception {
        try {
            String[] coordinates = location.split(",");
            if (coordinates.length != 2) {
                throw new IllegalArgumentException("Location should be in format 'latitude,longitude'");
            }

            double latitude = Double.parseDouble(coordinates[0].trim());
            double longitude = Double.parseDouble(coordinates[1].trim());

            Dotenv dotenv = Dotenv.load();

            // Call GeoNames API to get country based on coordinates
            HttpResponse<String> geonamesResponse = Unirest
                    .get("http://api.geonames.org/countryCodeJSON")
                    .queryString("lat", latitude)
                    .queryString("lng", longitude)
                    .queryString("username", dotenv.get("GEONAMES_USERNAME"))
                    .asString();

            if (geonamesResponse.getStatus() != 200) {
                throw new RuntimeException("Failed to fetch country code: " + geonamesResponse.getStatusText());
            }

            JsonObject countryData = JsonParser.parseString(geonamesResponse.getBody()).getAsJsonObject();
            String countryCode = countryData.get("countryCode").getAsString();

            // First try to find an exact match for the country code
            if (locationsData.has(countryCode)) {
                return countryCode;
            }

            // If no exact match, find any codes that start with the country code
            for (Map.Entry<String, JsonElement> entry : locationsData.entrySet()) {
                if (entry.getKey().startsWith(countryCode + "-")) {
                    return entry.getKey();
                }
            }

            // If no location found, return empty string for worldwide
            return "";
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            return "";
        }
    }

    public JsonObject fetchInfo(Object query, String location) {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("SERP_API_KEY");

        // Create parameters for the API request
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("engine", "google_trends");

        // Use the query parameter which represents the general category
        if (query instanceof String) {
            parameters.put("q", query);
        } else if (query instanceof String[]) {
            parameters.put("q", String.join(",", (String[]) query));
        } else {
            parameters.put("q", query.toString());
        }

        parameters.put("data_type", "TIMESERIES");
        parameters.put("date", "now 7-d");
        parameters.put("en", "en");
        parameters.put("api_key", key);

        // Only process location if it's not empty
        try {
            if (location != null && !location.isEmpty() && !location.equals("") && location != "") {
                // Parse location string to get latitude and longitude

                // Get the Google Trends location code based on coordinates
                String locationCode = getLocationCode(location);

                // Find the latitude and longitude from the location string
                String[] coordinates = location.split(",");
                double latitude = Double.parseDouble(coordinates[0].trim());
                double longitude = Double.parseDouble(coordinates[1].trim());

                // Get timezone ID using the timezone API
                HttpResponse<String> timezoneResponse = Unirest
                        .get("http://api.geonames.org/timezoneJSON")
                        .queryString("lat", latitude)
                        .queryString("lng", longitude)
                        .queryString("username", dotenv.get("GEONAMES_USERNAME"))
                        .asString();

                if (timezoneResponse.getStatus() != 200) {
                    throw new RuntimeException("Failed to fetch timezone: " + timezoneResponse.getStatusText());
                }

                JsonObject timezoneData = JsonParser.parseString(timezoneResponse.getBody()).getAsJsonObject();
                int timeZoneOffset = timezoneData.get("rawOffset").getAsInt();

                parameters.put("geo", locationCode);
                parameters.put("tz", timeZoneOffset);
            }

            HttpResponse<String> serpResponse = Unirest
                    .get("https://serpapi.com/search")
                    .queryString(parameters)
                    .header("Content-Type", "application/json")
                    .header("cache-control", "no-cache")
                    .asString();

            int totalScore = getTotalScore(serpResponse.getBody());

            if (serpResponse.getStatus() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(serpResponse.getBody()).getAsJsonObject();
                jsonResponse.addProperty("score", totalScore);
                return jsonResponse;
            } else {
                throw new RuntimeException("Failed to fetch data: " + serpResponse.getBody());
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return null;
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int getTotalScore(String responseBody) {
        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();

        int totalScore = 0;

        if (response.has("interest_over_time") &&
                !response.get("interest_over_time").isJsonNull()) {

            JsonObject interestOverTime = response.getAsJsonObject("interest_over_time");

            if (interestOverTime.has("timeline_data") &&
                    !interestOverTime.get("timeline_data").isJsonNull()) {

                for (JsonElement timelineElement : interestOverTime.getAsJsonArray("timeline_data")) {
                    JsonObject timelineObject = timelineElement.getAsJsonObject();

                    if (timelineObject.has("values") &&
                            !timelineObject.get("values").isJsonNull()) {

                        for (JsonElement valueElement : timelineObject.getAsJsonArray("values")) {
                            JsonObject valueObject = valueElement.getAsJsonObject();

                            if (valueObject.has("value") &&
                                    !valueObject.get("value").isJsonNull()) {

                                totalScore += valueObject.get("value").getAsInt();
                            }
                        }
                    }
                }
            }
        }

        return totalScore;
    }
}
