package trendData.googleTrendsData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
            throw new RuntimeException("Error loading Google Trends locations data", e);
        }
    }

    private String getLocationCode(double latitude, double longitude) throws Exception {
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
    }

    public JsonObject fetchInfo(Object query, String location) throws Exception {
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
        parameters.put("date", "today 1-m");
        parameters.put("en", "en");
        parameters.put("api_key", key);

        // Only process location if it's not empty
        if (location != null && !location.isEmpty() && !location.equals("") && location != "") {
            // Parse location string to get latitude and longitude
            String[] coordinates = location.split(",");
            if (coordinates.length != 2) {
                throw new IllegalArgumentException("Location should be in format 'latitude,longitude'");
            }
            double latitude = Double.parseDouble(coordinates[0].trim());
            double longitude = Double.parseDouble(coordinates[1].trim());

            // Get the Google Trends location code based on coordinates
            String locationCode = getLocationCode(latitude, longitude);

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

        System.out.println("Response: " + serpResponse.getBody());

        if (serpResponse.getStatus() == 200) {
            return JsonParser.parseString(serpResponse.getBody()).getAsJsonObject();
        } else {
            throw new RuntimeException("Failed to fetch data: " + serpResponse.getStatusText());
        }
    }
}
