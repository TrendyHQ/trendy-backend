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
        private boolean isFutureRequest;
        private String userId;

        public String getMessage() {
            return message;
        }

        public String getUserLocation() {
            return userLocation;
        }

        public boolean getIsFutureRequest() {
            return isFutureRequest;
        }

        public String getUserId() {
            return userId;
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
        private String trendCategory;
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

        public String getTrendCategory() {
            return trendCategory;
        }
    }

    public static class FavoritePostObject {
        private String postId;
        private String postCategory;

        public FavoritePostObject(String postId, String postCategory) {
            this.postId = postId;
            this.postCategory = postCategory;
        }

        public String getPostId() {
            return postId;
        }

        public String getPostCategory() {
            return postCategory;
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
        private boolean userHasLiked;
        private boolean userHasDisliked;

        public PostInfoObject(int likes, CommentObject[] comments, boolean userHasLiked, boolean userHasDisliked) {
            this.likes = likes;
            this.comments = comments;
            this.userHasLiked = userHasLiked;
            this.userHasDisliked = userHasDisliked;
        }

        public int getLikes() {
            return likes;
        }

        public CommentObject[] getComments() {
            return comments;
        }
        
        public boolean getUserHasLiked() {
            return userHasLiked;
        }
    
        public boolean getUserHasDisliked() {
            return userHasDisliked;
        }
    }

    public static class FeedbackObject {
        private String userId;
        private String feedback;

        public FeedbackObject(String userId, String feedback) {
            this.userId = userId;
            this.feedback = feedback;
        }

        public String getUserId() {
            return userId;
        }

        public String getFeedback() {
            return feedback;
        }
    }

    public static class FeedbackRequest {
        private String userId;
        private String feedback;
        private boolean isReport;

        public String getUserId() {
            return userId;
        }

        public String getFeedback() {
            return feedback;
        }

        public boolean getIsReport() {
            return isReport;
        }
    }

    public static class LikeRequest {
        private String userId;
        private String postId;
        private int like;

        public String getPostId() {
            return postId;
        }

        public String getUserId() {
            return userId;
        }

        public int getLike() {
            return like;
        }
    }

    public static class PostLikesObject {
        private int likes;
        private String[] usersThatLiked;
        private String[] usersThatDisliked;

        public PostLikesObject(int likes, String[] usersThatLiked, String[] usersThatDisliked) {
            this.usersThatLiked = usersThatLiked;
            this.usersThatDisliked = usersThatDisliked;
            this.likes = likes;
        }

        public int getLikes() {
            return likes;
        }

        public String[] getUsersThatLiked() {
            return usersThatLiked;
        }
    
        public String[] getUsersThatDisliked() {
            return usersThatDisliked;
        }
    }

    public static class TopRedditRequest {
        private int requestAmount;
        private String userId;

        public int getRequestAmount() {
            return requestAmount;
        }

        public String getUserId() {
            return userId;
        }
    }
}
