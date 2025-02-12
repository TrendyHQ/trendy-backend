package trendData.redditData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import dataManagement.UserManager;
import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.models.SubredditSearchSort;
import net.dean.jraw.pagination.DefaultPaginator;
import net.dean.jraw.pagination.Paginator;
import net.dean.jraw.references.SubredditReference;
import net.dean.jraw.models.SubredditSort;

public class RedditDataFetcher {
    public RedditPost[] getData(String subredditName, RedditClientManager redditClientManager, int limit)
            throws SQLException {

        // Disable logging for JRAW
        Logger jrawLogger = (Logger) LoggerFactory.getLogger("net.dean.jraw");
        jrawLogger.setLevel(Level.OFF);

        if (redditClientManager.getClient() == null) {
            redditClientManager.autherizeClient();
        }

        RedditClient redditClient = redditClientManager.getClient();

        if (redditClient != null) {
            // Access a subreddit
            Subreddit closestMatch = null;
            try {
                Paginator<Subreddit> paginator = redditClient.searchSubreddits()
                        .query(subredditName)
                        .limit(5)
                        .sorting(SubredditSearchSort.RELEVANCE)
                        .build();

                int maxSubscribers = 100000;

                for (Subreddit subreddit : paginator.next()) {
                    try {
                        if (subreddit.getSubscribers() > maxSubscribers) {
                            maxSubscribers = subreddit.getSubscribers();
                            closestMatch = subreddit;
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
            }

            String query;
            if (closestMatch == null) {
                query = subredditName;
            } else {
                query = closestMatch.getName();
            }

            SubredditReference subreddit = redditClient.subreddit(query);

            // Fetch top posts
            DefaultPaginator<Submission> topPosts = subreddit.posts()
                    .sorting(SubredditSort.HOT)
                    .limit(limit)
                    .build();

            // Collect the titles of the top posts
            List<RedditPost> posts = new ArrayList<>();

            for (Submission post : topPosts.next()) {
                String postId = post.getId();
                int score = post.getScore();
                String moreInfo = post.getSelfText();
                String link = post.getUrl();

                // Store updated post data
                if (!post.getTitle().contains("r/") && !post.isNsfw()) {
                    int moreRelevantValue = new TrendAnalyzer().isPostGoingUp(postId, post);

                    RedditDataStorage storage = new RedditDataStorage();
                    storage.storeRedditPostData(post);

                    posts.add(new RedditPost(post.getTitle(), subredditName, moreRelevantValue, score, moreInfo, link,
                            postId));
                }
            }

            RedditPost[] arrayOfPosts = new RedditPost[posts.size()];
            for (int i = 0; i < posts.size(); i++) {
                arrayOfPosts[i] = posts.get(i);
            }

            return arrayOfPosts;
        }
        return null;
    }

    public FavoritePost[] getFavoritePosts(String userId, RedditClientManager redditClientManager) throws SQLException {
        ArrayList<String> postIds = new UserManager().getUsersFavoritePostsIds(userId);

        if (redditClientManager.getClient() == null) {
            redditClientManager.autherizeClient();
        }

        RedditClient redditClient = redditClientManager.getClient();

        // Collect the titles of the top posts
        List<FavoritePost> posts = new ArrayList<>();

        if (redditClient != null) {
            postIds.forEach((postId) -> {
                try {
                    Submission post = redditClient.submission(postId).inspect();

                    int score = post.getScore();
                    String moreInfo = post.getSelfText();
                    String link = post.getUrl();
    
                    if (!post.getTitle().contains("r/") && !post.isNsfw()) {        
                        posts.add(new FavoritePost(post.getTitle(), score, moreInfo, link,
                                postId));
                    }    
                } catch (Exception e) {
                }
            });

            FavoritePost[] arrayOfPosts = new FavoritePost[posts.size()];
            for (int i = 0; i < posts.size(); i++) {
                arrayOfPosts[i] = posts.get(i);
            }

            return arrayOfPosts;
        }

        return new FavoritePost[0];
    }

    @SuppressWarnings("unused")
    public static class RedditPost {
        private int score;
        private String title;
        private String category;
        private int moreRelevantValue;
        private String moreInfo;
        private String link;
        private String id;

        public RedditPost(String title, String category, int moreRelevantValue, int score, String moreInfo,
                String link, String id) {
            this.title = title;
            this.category = category;
            this.moreRelevantValue = moreRelevantValue;
            this.score = score;
            this.moreInfo = moreInfo;
            this.link = link;
            this.id = id;
        }

        public int getScore() {
            return score;
        }
    }

    @SuppressWarnings("unused")
    public static class FavoritePost {
        private int score;
        private String title;
        private String moreInfo;
        private String link;
        private String id;

        public FavoritePost(String title, int score, String moreInfo,
                String link, String id) {
            this.title = title;
            this.score = score;
            this.moreInfo = moreInfo;
            this.link = link;
            this.id = id;
        }

        public int getScore() {
            return score;
        }
    }
}
