package com.spotlight.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GameRoom implements Serializable {
    private String roomCode;
    private String hostId;
    private Map<String, Player> players = new HashMap<>();
    private String currentQuestion;
    private String spotlightPlayerId;
    private String status; // WAITING, IN_PROGRESS, FINISHED
    private Map<String, String> guesses = new HashMap<>();
    private Map<String, String> votes = new HashMap<>();
    private java.util.List<String> logs = new java.util.ArrayList<>();

    public GameRoom() {
        // Required for Firebase
    }

    public GameRoom(String roomCode, String hostId) {
        this.roomCode = roomCode;
        this.hostId = hostId;
        this.status = "WAITING";
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public Map<String, Player> getPlayers() {
        return players == null ? new HashMap<>() : players;
    }

    public void setPlayers(Map<String, Player> players) {
        this.players = players;
    }

    public String getCurrentQuestion() {
        return currentQuestion;
    }

    public void setCurrentQuestion(String currentQuestion) {
        this.currentQuestion = currentQuestion;
    }

    public String getSpotlightPlayerId() {
        return spotlightPlayerId;
    }

    public void setSpotlightPlayerId(String spotlightPlayerId) {
        this.spotlightPlayerId = spotlightPlayerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getGuesses() {
        return guesses == null ? new HashMap<>() : guesses;
    }

    public void setGuesses(Map<String, String> guesses) {
        this.guesses = guesses;
    }

    public Map<String, String> getVotes() {
        return votes == null ? new HashMap<>() : votes;
    }

    public void setVotes(Map<String, String> votes) {
        this.votes = votes;
    }

    public java.util.List<String> getLogs() {
        return logs == null ? new java.util.ArrayList<>() : logs;
    }

    public void setLogs(java.util.List<String> logs) {
        this.logs = logs;
    }
}
