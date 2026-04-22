package com.spotlight.logic;

import androidx.lifecycle.LiveData;

import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.logic.GameViewModel.Phase;
import com.spotlight.model.RoomStatus;

import java.util.List;
import java.util.Set;

public interface GameSession {

    // --- Lifecycle & Initialization ---
    void init(String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category);
    void leaveGame();
    void cleanup();

    // --- Core Game Actions ---
    void submitAnswer(String answer);
    void submitVote(String vote);
    void startNextRound();
    void calculateScores(Set<String> matchedAnswers);
    void updateStatus(RoomStatus status);

    // --- Local-Specific Actions ---
    void toggleMatch(String answer);
    void deleteChoice(int choiceIndex);

    // --- UI State Observables (LiveData) ---
    LiveData<Phase> getCurrentPhase();
    LiveData<Question> getCurrentQuestion();
    LiveData<List<Player>> getPlayers();
    LiveData<Integer> getSpotlightPlayerIndex();
    LiveData<Integer> getCurrentPlayerIndex();
    LiveData<List<String>> getCurrentChoices();
    LiveData<List<String>> getLogs();

    // --- State Getters ---
    boolean isHost();
    Set<String> getMatchedAnswers();
    Set<Integer> getDeletedChoiceIndices();
    List<String> getLocalAnswers();
}