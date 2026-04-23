package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.model.RoomStatus;

import java.util.List;
import java.util.Set;

public class GameViewModel extends ViewModel {

    public enum Phase {
        WAITING_FOR_ANSWERS,
        REVIEW,
        VOTING,
        RESULTS,
        FINISHED
    }

    private final GameRepository gameRepository;
    private final QuestionRepository questionRepository;

    private GameSession session;
    private String roomCode;

    public GameViewModel(GameRepository gameRepository, QuestionRepository questionRepository) {
        this.gameRepository = gameRepository;
        this.questionRepository = questionRepository;
    }

    public void init(boolean isMultiplayer, String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category) {
        this.roomCode = roomCode;

        if (isMultiplayer) {
            session = new MultiplayerGameSession(gameRepository, questionRepository);
        } else {
            session = new LocalGameSession(questionRepository);
        }

        questionRepository.loadQuestionsAsync(() -> {
            session.init(roomCode, playerId, hostId, initialPlayers, category);
        });
    }

    // ======================================================================
    // UI ACTIONS DELEGATED TO THE SESSION
    // ======================================================================

    public void startNewRoundLocal() { session.startNextRound(); }
    public void startNextMultiplayerRound() { session.startNextRound(); }

    public void startVotingLocal() { session.updateStatus(RoomStatus.VOTING); }

    // THE FIX: It now correctly passes the requested status instead of hardcoding VOTING!
    public void updateMultiplayerStatus(RoomStatus status) { session.updateStatus(status); }

    public void calculateLocalScores() { session.calculateScores(null); }
    public void calculateMultiplayerScores(Set<String> matched) { session.calculateScores(matched); }

    public void submitLocalAnswer(String answer) { session.submitAnswer(answer); }
    public void submitMultiplayerAnswer(String answer) { session.submitAnswer(answer); }

    public void submitLocalVote(String vote) { session.submitVote(vote); }
    public void submitMultiplayerVote(String vote) { session.submitVote(vote); }

    public void toggleLocalMatch(String answer) { session.toggleMatch(answer); }
    public void deleteLocalChoice(int choiceIndex) { session.deleteChoice(choiceIndex); }

    public void leaveMultiplayerRoom() { session.leaveGame(); }

    // ======================================================================
    // LIVEDATA OBSERVERS DELEGATED TO THE SESSION
    // ======================================================================

    public LiveData<Phase> getCurrentPhase() { return session.getCurrentPhase(); }
    public LiveData<Question> getCurrentQuestion() { return session.getCurrentQuestion(); }
    public LiveData<List<Player>> getPlayers() { return session.getPlayers(); }
    public LiveData<Integer> getSpotlightPlayerIndex() { return session.getSpotlightPlayerIndex(); }
    public LiveData<Integer> getCurrentPlayerIndexLiveData() { return session.getCurrentPlayerIndex(); }
    public LiveData<List<String>> getCurrentChoices() { return session.getCurrentChoices(); }
    public LiveData<List<String>> getLogs() { return session.getLogs(); }

    public boolean isHost() { return session.isHost(); }
    public int getCurrentPlayerIndex() {
        Integer val = session.getCurrentPlayerIndex().getValue();
        return val != null ? val : 0;
    }
    public List<String> getLocalAnswers() { return session.getLocalAnswers(); }
    public Set<String> getLocalMatchedAnswers() { return session.getMatchedAnswers(); }
    public Set<Integer> getLocalDeletedPlayerIndices() { return session.getDeletedChoiceIndices(); }

    public LiveData<GameRoom> getRoomData() { return gameRepository.getRoomData(roomCode); }

    public void setPhase(Phase phase) {}
    public void setCurrentQuestion(String question) {}
    public void setSpotlightPlayerId(String id) {}
    public void setPlayers(List<Player> players) {}
    public void setLogs(List<String> logs) {}

    @Override
    protected void onCleared() {
        super.onCleared();
        if (session != null) session.cleanup();
        gameRepository.cleanup();
    }
}