package dataManagement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import io.github.cdimascio.dotenv.Dotenv;
import structure.TrendyClasses.FeedbackObject;

public class FeedbackManager {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    public void addFeedbackToDatabase(FeedbackObject feedback, boolean isReport) {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String postInsertQuery = "INSERT INTO user_feedback (user_id, feedback, is_report) VALUES (?, ?, ?) ";

            try (PreparedStatement postStmt = connection.prepareStatement(postInsertQuery)) {
                postStmt.setString(1, feedback.getUserId());
                postStmt.setString(2, feedback.getFeedback());
                postStmt.setBoolean(3, isReport);
                postStmt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();    
        }
    }
}
