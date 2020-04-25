package ch.uzh.ifi.seal.soprafs20.rest.dto;

import ch.uzh.ifi.seal.soprafs20.constant.UserStatus;

public class PlayerGetDTO {
    private String username;
//    private UserStatus status;
    private boolean clueIsSent;
    private int score;
    private boolean guesser;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

//    public UserStatus getStatus() {
//        return status;
//    }
//
//    public void setStatus(UserStatus status) {
//        this.status = status;
//    }

    public boolean isClueIsSent() {
        return clueIsSent;
    }

    public void setClueIsSent(boolean clueIsSent) {
        this.clueIsSent = clueIsSent;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isGuesser() {
        return guesser;
    }

    public void setGuesser(boolean guesser) {
        this.guesser = guesser;
    }
}
