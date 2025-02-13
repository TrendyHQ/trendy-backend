package trendData.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import com.google.gson.Gson;

import controller.MainController.CommentObject;
import io.github.cdimascio.dotenv.Dotenv;

public class StorageManager {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

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

    public void putCommentOnPost(String postId, CommentObject comment) throws SQLException {
        CommentObject[] existingComments = getCommentsOnPost(postId);

        // Append the new comment if there are existing comments; otherwise, start a new
        // array
        CommentObject[] updatedComments = (existingComments != null)
                ? Arrays.copyOf(existingComments, existingComments.length + 1)
                : new CommentObject[1];

        if (existingComments != null) {
            for (CommentObject existingComment : existingComments) {
                System.out.println(existingComment.getValue());
            }
        }

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
}
