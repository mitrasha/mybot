package com.example;

import java.util.Date;

public class AuthorStats {
    private String username;
    private int postsCount;
    private int totalReactions;
    private Date lastActivity;

    public void addPost(int reactions) {
        postsCount++;
        totalReactions += reactions;
        lastActivity = new Date();
    }

    public String getUsername() { return username; }
    public int getPostsCount() { return postsCount; }
    public int getTotalReactions() { return totalReactions; }
    public Date getLastActivity() { return lastActivity; }
    public double getAverageReactions() { return postsCount == 0 ? 0 : (double) totalReactions / postsCount; }
    
    public void setUsername(String username) { this.username = username; }
}