package auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.cdimascio.dotenv.Dotenv;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;

public class AuthRequestManager {
    static Dotenv dotenv = Dotenv.load();

    final static String DOMAIN = dotenv.get("AUTH0_DOMAIN");
    final static String CLIENT_ID = dotenv.get("MANAGEMENT_AUTH0_CLIENT_ID");
    final static String CLIENT_SECRET = dotenv.get("MANAGEMENT_AUTH0_CLIENT_SECRET");

    public static String getAccessToken() throws Exception {
        String jsonBody = "{\"client_id\":\"" + CLIENT_ID + "\",\"client_secret\":\"" + CLIENT_SECRET
                + "\",\"audience\":\"https://" + DOMAIN + "/api/v2/\",\"grant_type\":\"client_credentials\"}";

        HttpResponse<String> response = Unirest.post("https://" + DOMAIN + "/oauth/token")
                .header("content-type", "application/json")
                .body(jsonBody)
                .asString();

        JsonObject jsonResponse = JsonParser.parseString(response.getBody()).getAsJsonObject();
        String accessToken = jsonResponse.get("access_token").getAsString();

        return accessToken;
    }

    public static void setUserInformation(String requestBody, String accessToken, String userId) throws Exception {
        String encodedUserId;
        if (userId.contains("%")) {
            encodedUserId = userId;
        } else {
            encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());
        }

        @SuppressWarnings("unused")
        HttpResponse<String> auth0ApiResponse = Unirest
                .patch("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                .header("authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("cache-control", "no-cache")
                .body(requestBody)
                .asString();

    }

    public static JsonObject getAuth0Info(String userId) throws Exception {
        String accessToken = getAccessToken();

        String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());
        HttpResponse<String> auth0ApiResponse = Unirest
                .get("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                .header("authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("cache-control", "no-cache")
                .asString();

        return JsonParser.parseString(auth0ApiResponse.getBody()).getAsJsonObject();
    }

    public void deleteUser(String userId) throws Exception {
        String accessToken = getAccessToken();

        String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8.toString());

        @SuppressWarnings("unused")
        HttpResponse<String> auth0ApiResponse = Unirest
                .delete("https://" + DOMAIN + "/api/v2/users/" + encodedUserId)
                .header("authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("cache-control", "no-cache")
                .asString();
    }
}
