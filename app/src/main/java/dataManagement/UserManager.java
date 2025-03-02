package dataManagement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import io.github.cdimascio.dotenv.Dotenv;

import structure.TrendyClasses.FavoritePostObject;;

public class UserManager {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    public void saveTrendForUser(String userId, String trendId, boolean saveTrend, String trendCategory) throws SQLException {
        String date = java.time.LocalDate.now().toString();

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            if (saveTrend) {
                String postInsertQuery = "INSERT INTO user_trends (user_id, post_id, date, post_category) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE post_id = post_id";
                try (PreparedStatement postStmt = connection.prepareStatement(postInsertQuery)) {
                    postStmt.setString(1, userId);
                    postStmt.setString(2, trendId);
                    postStmt.setString(3, date);
                    postStmt.setString(4, trendCategory);
                    postStmt.executeUpdate();
                }
            } else {
                String deleteQuery = "DELETE FROM user_trends WHERE user_id = ? AND post_id = ?";
                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteQuery)) {
                    deleteStmt.setString(1, userId);
                    deleteStmt.setString(2, trendId);
                    deleteStmt.executeUpdate();
                }
            }
        }
    }

    public ArrayList<FavoritePostObject> getUsersFavoritePostsIds(String userId) {
        ArrayList<FavoritePostObject> savedTrends = new ArrayList<>();

        String query = "SELECT post_id, post_category FROM user_trends WHERE user_id = ?";


        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                savedTrends.add(new FavoritePostObject(rs.getString("post_id"), rs.getString("post_category")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return savedTrends;
    }
}
