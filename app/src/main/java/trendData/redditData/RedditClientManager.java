package trendData.redditData;

import io.github.cdimascio.dotenv.Dotenv;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;

public class RedditClientManager {
    Dotenv dotenv = Dotenv.load();

    private String username = dotenv.get("REDDIT_USERNAME");
    private String password = dotenv.get("REDDIT_PASSWORD");
    private String clientId = dotenv.get("REDDIT_CLIENT_ID");
    private String clientSecret = dotenv.get("REDDIT_CLIENT_SECRET");

    private RedditClient redditClient = null;

    public RedditClient getClient() {
        return redditClient;
    }

    public Credentials getCredentials() {
        // Set up Reddit API credentials
        Credentials credentials = Credentials.script(
                username,
                password,
                clientId,
                clientSecret);

        return credentials;
    }

    public UserAgent getAgent() {
        // Create a UserAgent
        return new UserAgent("bot", "com.trendy.dataFetcher", "v1.0", username);
    }

    public void autherizeClient(){
        System.out.println("Authenticating Reddit client...");
        redditClient = OAuthHelper.automatic(new OkHttpNetworkAdapter(getAgent()), getCredentials());
    }
}
