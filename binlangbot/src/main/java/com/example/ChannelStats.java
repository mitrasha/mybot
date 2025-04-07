package com.example;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChannelStats {
    private final Map<Long, AuthorStats> authors = new ConcurrentHashMap<>();
    private final List<Post> posts = new ArrayList<>();

    public void addPost(Long authorId, String username, long messageId, Date date, int reactions) {
        authors.putIfAbsent(authorId, new AuthorStats());
        AuthorStats author = authors.get(authorId);
        author.setUsername(username);
        author.addPost(reactions);
        posts.add(new Post(authorId, messageId, date, reactions));
    }

    public Map<Long, AuthorStats> getAuthors() { return authors; }
    public List<Post> getPosts() { return posts; }
    
    public int getTotalReactions() { return posts.stream().mapToInt(Post::getReactions).sum(); }
    
    public double getAverageReactions() { return posts.isEmpty() ? 0 : (double) getTotalReactions() / posts.size(); }
    
    public List<Post> getTopPosts(int n) {
        return posts.stream()
            .sorted(Comparator.comparingInt(Post::getReactions).reversed())
            .limit(n)
            .collect(Collectors.toList());
    }
}