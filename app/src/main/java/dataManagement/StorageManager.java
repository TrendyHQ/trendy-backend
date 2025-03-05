package dataManagement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import com.google.gson.Gson;

import io.github.cdimascio.dotenv.Dotenv;

import structure.TrendyClasses.CommentObject;
import structure.TrendyClasses.PostInfoObject;
import structure.TrendyClasses.PostLikesObject;

public class StorageManager {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    public PostLikesObject getLikesOnPost(String postId) throws SQLException {
        String query = "SELECT trend_likes, users_that_liked FROM trend_information WHERE trend_id = ? LIMIT 1";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new PostLikesObject(rs.getInt("trend_likes"),
                        new Gson().fromJson(rs.getString("users_that_liked"), String[].class));
            } else {
                return new PostLikesObject(0, new String[0]);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new PostLikesObject(0, new String[0]);
        }
    }

    public CommentObject[] getCommentsOnPost(String postId) throws SQLException {
        String query = "SELECT trend_comments FROM trend_information WHERE trend_id = ? ORDER BY created_at DESC LIMIT 1";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            ResultSet rs = stmt.executeQuery();

            // Check if there is a previous day's record
            if (rs.next()) {
                CommentObject[] result = new Gson().fromJson(rs.getString("trend_comments"), CommentObject[].class);

                return result;
            } else {
                // No previous day's record found
                return new CommentObject[0];
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return new CommentObject[0];
        }
    }

    public PostInfoObject getInformationOnPost(String postId, String userId) throws SQLException {
        int postLikes = getLikesOnPost(postId).getLikes();
        String[] usersThatLiked = getLikesOnPost(postId).getUsersThatLiked();

        boolean userHasLiked = false;
        if (usersThatLiked != null) {
            userHasLiked = Arrays.asList(usersThatLiked).contains(userId);
        }

        CommentObject[] postComments = getCommentsOnPost(postId);

        return new PostInfoObject(postLikes, postComments, userHasLiked);
    }

    public void putCommentOnPost(String postId, CommentObject comment) throws SQLException {
        CommentObject[] existingComments = getCommentsOnPost(postId);

        // Append the new comment if there are existing comments; otherwise, start a new
        // array
        CommentObject[] updatedComments = (existingComments != null)
                ? Arrays.copyOf(existingComments, existingComments.length + 1)
                : new CommentObject[1];

        updatedComments[updatedComments.length - 1] = comment;

        String query = "INSERT INTO trend_information (trend_id, trend_comments) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE trend_comments = VALUES(trend_comments)";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            stmt.setString(2, new Gson().toJson(updatedComments));
            stmt.executeUpdate();
        }
    }

    public int setLikesOnPost(String userId, String postId, int likeState) throws SQLException {
        Gson gson = new Gson();

        // Get current post information
        String query = "SELECT trend_likes, users_that_liked, users_that_disliked FROM trend_information WHERE trend_id = ? LIMIT 1";
        int currentLikes = 0;
        String[] usersThatLiked = new String[0];
        String[] usersThatDisliked = new String[0];
        
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                currentLikes = rs.getInt("trend_likes");
                
                String likedJson = rs.getString("users_that_liked");
                if (likedJson != null && !likedJson.isEmpty()) {
                    usersThatLiked = gson.fromJson(likedJson, String[].class);
                }
                
                String dislikedJson = rs.getString("users_that_disliked");
                if (dislikedJson != null && !dislikedJson.isEmpty()) {
                    usersThatDisliked = gson.fromJson(dislikedJson, String[].class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Check current user state
        boolean userHasLiked = Arrays.asList(usersThatLiked).contains(userId);
        boolean userHasDisliked = Arrays.asList(usersThatDisliked).contains(userId);
        
        // Calculate the change in likes count
        int likesChange = 0;
        
        // Remove user from both arrays initially
        usersThatLiked = Arrays.stream(usersThatLiked)
            .filter(id -> !id.equals(userId))
            .toArray(String[]::new);
            
        usersThatDisliked = Arrays.stream(usersThatDisliked)
            .filter(id -> !id.equals(userId))
            .toArray(String[]::new);
        
        // Add user to appropriate array based on new state
        if (likeState > 0) {  // Like
            usersThatLiked = appendToArray(usersThatLiked, userId);
            likesChange = userHasLiked ? 0 : (userHasDisliked ? 2 : 1);
        } else if (likeState < 0) { // Dislike
            usersThatDisliked = appendToArray(usersThatDisliked, userId);
            likesChange = userHasDisliked ? 0 : (userHasLiked ? -2 : -1);
        } else { // Neutral
            likesChange = userHasLiked ? -1 : (userHasDisliked ? 1 : 0);
        }
        
        // Update the database
        int newLikesCount = currentLikes + likesChange;
        
        String updateQuery = "INSERT INTO trend_information (trend_id, trend_likes, users_that_liked, users_that_disliked) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE trend_likes = VALUES(trend_likes), " +
                "users_that_liked = VALUES(users_that_liked), users_that_disliked = VALUES(users_that_disliked)";
                
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
            stmt.setString(1, postId);
            stmt.setInt(2, newLikesCount);
            stmt.setString(3, gson.toJson(usersThatLiked));
            stmt.setString(4, gson.toJson(usersThatDisliked));
            stmt.executeUpdate();
        }
        
        return newLikesCount;
    }
    
    private String[] appendToArray(String[] array, String value) {
        String[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = value;
        return newArray;
    }
}
