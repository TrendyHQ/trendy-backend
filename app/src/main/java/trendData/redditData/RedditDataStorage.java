package trendData.redditData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;
import net.dean.jraw.models.Submission;

public class RedditDataStorage {
    Dotenv dotenv = Dotenv.load();

    private final String DB_URL = dotenv.get("DB_URL");
    private final String USER = dotenv.get("DB_USER");
    private final String PASSWORD = dotenv.get("DB_PASSWORD");

    public void storeRedditPostData(Submission post) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            String postInsertQuery = "INSERT INTO reddit_posts (post_id, title, subreddit) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE post_id = post_id";
            try (PreparedStatement postStmt = connection.prepareStatement(postInsertQuery)) {
                postStmt.setString(1, post.getId());
                postStmt.setString(2, post.getTitle());
                postStmt.setString(3, post.getSubreddit());
                postStmt.executeUpdate();
            }

            String trendInsertQuery = "INSERT INTO post_trends (post_id, date, score, num_comments) " +
                    "VALUES (?, CURDATE(), ?, ?) " +
                    "ON DUPLICATE KEY UPDATE score = VALUES(score), num_comments = VALUES(num_comments)";
            try (PreparedStatement trendStmt = connection.prepareStatement(trendInsertQuery)) {
                trendStmt.setString(1, post.getId());
                trendStmt.setInt(2, post.getScore());
                trendStmt.setInt(3, post.getCommentCount());
                trendStmt.executeUpdate();
            }
        }
    }
}
