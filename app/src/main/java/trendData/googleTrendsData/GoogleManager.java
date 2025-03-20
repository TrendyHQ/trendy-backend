package trendData.googleTrendsData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import structure.TrendyClasses.TimelineData;

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

    public String getLocationCode(String location) throws Exception {
        Dotenv dotenv = Dotenv.load();

        String[] coordinates = location.split(",");
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("Location should be in format 'latitude,longitude'");
        }
        double latitude = Double.parseDouble(coordinates[0].trim());
        double longitude = Double.parseDouble(coordinates[1].trim());

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

    /**
     * Fetches trend information from Google Trends API via SerpAPI.
     * 
     * This method takes a search query and an optional location parameter to
     * retrieve
     * time series trend data from Google Trends. It handles different query formats
     * and adds geolocation context when a location is provided.
     *
     * @param query    The search term(s) to analyze. Can be a String for a single
     *                 query,
     *                 String[] for multiple queries, or any object that can be
     *                 converted to a string
     * @param location A string representing geographic coordinates in the format
     *                 "latitude,longitude".
     *                 If null or empty, no location-specific data will be fetched
     * @return JsonObject containing the Google Trends data with an added
     *         "isTrending" property
     *         that indicates whether the query is currently trending
     * @throws Exception If there are errors in parsing location data, fetching
     *                   timezone information,
     *                   calling the SerpAPI, or processing the response
     */
    public JsonObject fetchInfo(Object query, String location) throws Exception {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("SERP_API_KEY");

        // Create parameters for the API request
        try {
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
                // Get the Google Trends location code based on coordinates
                String locationCode = getLocationCode(location);

                String[] coordinates = location.split(",");
                if (coordinates.length != 2) {
                    throw new IllegalArgumentException("Location should be in format 'latitude,longitude'");
                }
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

            if (serpResponse.getStatus() == 200) {
                JsonObject data = JsonParser.parseString(serpResponse.getBody()).getAsJsonObject();
                boolean dataIsTrending = processData(data);

                data.addProperty("isTrending", dataIsTrending);

                return data;
            } else {
                throw new RuntimeException("Failed to fetch data: " + serpResponse.getStatusText());
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * This method processes the data to determine if the scores of each day are
     * going up over time by comparing to the average growth.
     * 
     * @param data A JsonObject that contains a list of data for a set time frame.
     * @return A boolean that indicates if the scores of each day are going up over
     *         time at a significant rate.
     */
    private boolean processData(JsonObject data) {
        JsonObject interestOverTime = data.get("interest_over_time").getAsJsonObject();
        JsonElement timelineElement = interestOverTime.get("timeline_data");
        TimelineData[] timelineDataArray = new Gson().fromJson(timelineElement, TimelineData[].class);

        if (timelineDataArray == null || timelineDataArray.length < 3) {
            return false;
        }

        // Apply linear regression
        int n = timelineDataArray.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        double maxValue = Double.MIN_VALUE;
        double minValue = Double.MAX_VALUE;
        double[] values = new double[n];

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = timelineDataArray[i].values[0].extracted_value;
            values[i] = y;

            if (y > maxValue)
                maxValue = y;
            if (y < minValue)
                minValue = y;

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Calculate slope
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        // Calculate data variability (range)
        double range = maxValue - minValue;

        // Calculate relative slope - how much the trend grows compared to the data
        // range
        double relativeSlope = (range > 0) ? (slope * (n - 1)) / range : 0;

        // Consider it trending if relative slope indicates at least 20% growth over the
        // period
        // compared to the overall range of values
        return relativeSlope > 0.15;
    }
}
