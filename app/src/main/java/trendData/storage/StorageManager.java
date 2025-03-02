package trendData.storage;

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

    public PostInfoObject getInformationOnPost(String postId) throws SQLException {
        int postLikes = getLikesOnPost(postId).getLikes();
        CommentObject[] postComments = getCommentsOnPost(postId);

        return new PostInfoObject(postLikes, postComments);
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

    public int setLikesOnPost(String userId, String postId, boolean isLike) throws SQLException {
        Gson gson = new Gson();

        PostLikesObject postLikesInfo = getLikesOnPost(postId);
        int postLikes = postLikesInfo.getLikes();

        String[] usersThatLikedArray = postLikesInfo.getUsersThatLiked();
        // Create a new array with length+1 to accommodate all existing users plus the new one
        String[] updatedUsersThatLikedArray = new String[usersThatLikedArray.length + 1];
        
        // Copy all existing users to the new array
        System.arraycopy(usersThatLikedArray, 0, updatedUsersThatLikedArray, 0, usersThatLikedArray.length);
        
        // Add the new userId at the end
        updatedUsersThatLikedArray[updatedUsersThatLikedArray.length - 1] = userId;

        String query = "INSERT INTO trend_information (trend_id, trend_likes, users_that_liked) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE trend_likes = VALUES(trend_likes), users_that_liked = VALUES(users_that_liked)";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            if (isLike) {
                stmt.setInt(2, postLikes + 1);
                stmt.setString(3,
                        gson.toJson(Arrays.stream(updatedUsersThatLikedArray).distinct().toArray(String[]::new)));
            } else {
                stmt.setInt(2, postLikes - 1);
                stmt.setString(3, gson.toJson(Arrays.stream(usersThatLikedArray).filter(user -> !user.equals(userId))
                        .toArray(String[]::new)));
            }
            stmt.executeUpdate();
        }

        return isLike ? postLikes + 1 : postLikes - 1;
    }
}
