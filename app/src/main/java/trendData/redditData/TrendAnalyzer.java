package trendData.redditData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;
import net.dean.jraw.models.Submission;

public class TrendAnalyzer {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    public int isPostGoingUp(String postId, Submission post) throws SQLException {
        String query = "SELECT score, num_comments, date FROM post_trends WHERE post_id = ? ORDER BY date DESC LIMIT 2";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, postId);
            ResultSet rs = stmt.executeQuery();

            // Retrieve latest day's record
            int latestScore = post.getScore();
            int latestComments = post.getCommentCount();

            // Check if there is a previous day's record
            if (rs.next()) {
                int previousScore = rs.getInt("score");
                int previousComments = rs.getInt("num_comments");

                int result = -1;

                if (latestScore > previousScore && latestComments > previousComments) {
                    result = 1;
                } else if(latestScore == previousScore && latestComments == previousComments) {
                    result = -1;
                } else {
                    result = 0;
                }
                return result;
            } else {
                // No previous day's record found
                return -1; // or handle as needed
            }
        }
    }
}
