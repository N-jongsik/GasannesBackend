package com.movie.MovieReview.post.dto;

import com.movie.MovieReview.post.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostDetailDto implements PostDtoInterface{
    private Long postId;
    private Long memberId;
    private String title;
    private String content;
    private String nickname;
    private Integer likesCount;
    private boolean liked;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private int commentCnt;
    @Override
    public Long getPostId() {
        return postId;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public boolean liked() {
        return liked;
    }

    @Override
    public Integer likesCount() {
        return likesCount;
    }

    @Override
    public LocalDateTime createdDate() {
        return createdDate;
    }

    @Override
    public LocalDateTime modifiedDate() {
        return modifiedDate;
    }

    public static PostDetailDto entityToDetailDto(Post post) {
        return PostDetailDto.builder()
                .postId(post.getPostId())
                .nickname(post.getWriter().getNickname())
                .title(post.getTitle())
                .content(post.getContent())
                .liked(post.isLiked())
                .likesCount(post.getLikesCount())
                .createdDate(post.getCreatedDate())
                .modifiedDate(post.getModifiedDate())
                .commentCnt(post.getCommentCnt())
                .build();
    }
}
