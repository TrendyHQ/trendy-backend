package structure;

public class TrendyClasses {
    public static class UserUpdateRequest {
        private String userId;
        private String newNickname;

        public String getUserId() {
            return userId;
        }

        public String getNewNickname() {
            return newNickname;
        }
    }

    public static class AiRequest {
        private String message;
        private String userLocation;
        private String userBirthdate;
        private String userGender;
        private boolean isFutureRequest;

        public String getMessage() {
            return message;
        }

        public String getUserLocation() {
            return userLocation;
        }

        public String getUserBirthdate() {
            return userBirthdate;
        }

        public String getUserGender() {
            return userGender;
        }

        public boolean getIsFutureRequest() {
            return isFutureRequest;
        }
    }

    public static class LoginRequest {
        private String userId;

        public String getUserId() {
            return userId;
        }
    }

    public static class GenderUpdateRequest {
        private String userId;
        private String gender;

        public String getUserId() {
            return userId;
        }

        public String getGender() {
            return gender;
        }
    }

    public static class TrendSaveRequest {
        private String userId;
        private String trendId;
        private boolean saveTrend;

        public String getUserId() {
            return userId;
        }

        public String getTrendId() {
            return trendId;
        }

        public boolean getSaveTrend() {
            return saveTrend;
        }
    }

    public static class UpdateUserRequest {
        private String userId;
        private String toUpdate;

        public String getUserId() {
            return userId;
        }

        public String getToUpdate() {
            return toUpdate;
        }
    }

    public static class PostData {
        private String title;
        private int score;
        private String moreInfo;
        private String link;
        private String postId;
        private String subredditName;
        private PostInfoObject otherInformation;

        public PostData(String title, int score, String moreInfo, String link, String postId, String subredditName,
                PostInfoObject otherInformation) {
            this.title = title;
            this.score = score;
            this.moreInfo = moreInfo;
            this.link = link;
            this.postId = postId;
            this.subredditName = subredditName;
            this.otherInformation = otherInformation;
        }

        public String getTitle() {
            return title;
        }

        public int getScore() {
            return score;
        }

        public String getMoreInfo() {
            return moreInfo;
        }

        public String getLink() {
            return link;
        }

        public String getPostId() {
            return postId;
        }

        public String getSubredditName() {
            return subredditName;
        }

        public PostInfoObject getOtherInformation() {
            return otherInformation;
        }
    }

    public static class CommentRequest {
        private String postId;
        private CommentObject comment;

        public String getPostId() {
            return postId;
        }

        public CommentObject getComment() {
            return comment;
        }
    }

    public static class CommentObject {
        private String userId;
        private String value;
        private String datePublished;
        private String nick;

        public String getUserId() {
            return userId;
        }

        public String getValue() {
            return value;
        }

        public String getDatePublished() {
            return datePublished;
        }

        public String getNick() {
            return nick;
        }
    }

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

        public String getTitle() {
            return title;
        }

        public String getCategory() {
            return category;
        }

        public int getMoreRelevantValue() {
            return moreRelevantValue;
        }

        public String getMoreInfo() {
            return moreInfo;
        }

        public String getLink() {
            return link;
        }

        public String getId() {
            return id;
        }
    }

    public static class SpecificPost {
        private int score;
        private String title;
        private String moreInfo;
        private String link;
        private String id;
        private String category;

        public SpecificPost(String title, int score, String moreInfo,
                String link, String id, String category) {
            this.title = title;
            this.score = score;
            this.moreInfo = moreInfo;
            this.link = link;
            this.id = id;
            this.category = category;
        }

        public int getScore() {
            return score;
        }

        public String getTitle() {
            return title;
        }

        public String getMoreInfo() {
            return moreInfo;
        }

        public String getLink() {
            return link;
        }

        public String getId() {
            return id;
        }

        public String getCategory() {
            return category;
        }
    }

    public static class PostInfoObject {
        private int likes;
        private CommentObject[] comments;

        public PostInfoObject(int likes, CommentObject[] comments) {
            this.likes = likes;
            this.comments = comments;
        }

        public int getLikes() {
            return likes;
        }

        public CommentObject[] getComments() {
            return comments;
        }
    }
}
