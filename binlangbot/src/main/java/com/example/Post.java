package com.example;

import java.util.Date;

public class Post {
    private final long authorId;
    private final long messageId;
    private final Date postDate;
    private final int reactions;

    public Post(long authorId, long messageId, Date postDate, int reactions) {
        this.authorId = authorId;
        this.messageId = messageId;
        this.postDate = postDate;
        this.reactions = reactions;
    }

    public long getAuthorId() { return authorId; }
    public long getMessageId() { return messageId; }
    public Date getPostDate() { return postDate; }
    public int getReactions() { return reactions; }
}