package com.spotlight.model;

import java.io.Serializable;

public class Player implements Serializable {
    private String id;
    private String name;
    private int score;
    private String currentAnswer;
    private long joinTimestamp;

    public Player() {
        // Required for Firebase
    }

    public Player(String name) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.score = 0;
        this.joinTimestamp = System.currentTimeMillis();
    }

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.joinTimestamp = System.currentTimeMillis();
    }

    public long getJoinTimestamp() {
        return joinTimestamp;
    }

    public void setJoinTimestamp(long joinTimestamp) {
        this.joinTimestamp = joinTimestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int points) {
        this.score += points;
    }

    public String getCurrentAnswer() {
        return currentAnswer;
    }

    public void setCurrentAnswer(String currentAnswer) {
        this.currentAnswer = currentAnswer;
    }
}
