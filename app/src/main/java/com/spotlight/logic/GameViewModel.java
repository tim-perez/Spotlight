package com.spotlight.logic;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameViewModel extends AndroidViewModel {

    public enum Phase {
        WAITING_FOR_ANSWERS,
        REVIEW,
        VOTING,
        RESULTS,
        FINISHED
    }

    private final GameRepository gameRepository;
    private final QuestionRepository questionRepository;
    
    private final MutableLiveData<Phase> currentPhase = new MutableLiveData<>();
    private final MutableLiveData<Question> currentQuestion = new MutableLiveData<>();
    private final MutableLiveData<List<Player>> players = new MutableLiveData<>();
    private final MutableLiveData<Integer> spotlightPlayerIndex = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> logs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> currentChoices = new MutableLiveData<>(new ArrayList<>());

    private String playerId;
    private String roomCode;
    private String hostId;
    private boolean isMultiplayer;

    // Local mode state
    private int spotlightIndex = 0;
    private int currentPlayerIndex = 0;
    private final List<String> localAnswers = new ArrayList<>();
    private final Set<Integer> localMatchedPlayerIndices = new HashSet<>();
    private final Set<Integer> localDeletedPlayerIndices = new HashSet<>();
    private final Map<Integer, String> localVotesMap = new HashMap<>();

    public GameViewModel(@NonNull Application application) {
        super(application);
        gameRepository = new GameRepository();
        questionRepository = new QuestionRepository(application);
    }

    public void init(boolean isMultiplayer, String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category) {
        this.isMultiplayer = isMultiplayer;
        this.roomCode = roomCode;
        this.playerId = playerId;
        this.hostId = hostId;
        this.players.setValue(initialPlayers);

        if (category != null && !category.equals("All")) {
            questionRepository.filterByCategory(category);
        }

        if (isMultiplayer) {
            setupMultiplayer();
        } else {
            startNewRoundLocal();
        }
    }

    private void setupMultiplayer() {
        // Multi-player logic is partially handled by Repository and Activity observing LiveData
    }

    public LiveData<GameRoom> getRoomData() {
        return gameRepository.getRoomData(roomCode);
    }

    public LiveData<Phase> getCurrentPhase() {
        return currentPhase;
    }

    public LiveData<Question> getCurrentQuestion() {
        return currentQuestion;
    }

    public LiveData<List<Player>> getPlayers() {
        return players;
    }

    public LiveData<Integer> getSpotlightPlayerIndex() {
        return spotlightPlayerIndex;
    }

    public LiveData<List<String>> getLogs() {
        return logs;
    }

    public LiveData<List<String>> getCurrentChoices() {
        return currentChoices;
    }

    public void startNewRoundLocal() {
        if (currentPhase.getValue() != null && currentPhase.getValue() != Phase.FINISHED) {
            spotlightIndex = (spotlightIndex + 1) % players.getValue().size();
            spotlightPlayerIndex.setValue(spotlightIndex);
        } else {
            spotlightIndex = 0;
            spotlightPlayerIndex.setValue(0);
        }
        currentPlayerIndex = spotlightIndex;
        localAnswers.clear();
        for (int i = 0; i < players.getValue().size(); i++) localAnswers.add(null);
        localMatchedPlayerIndices.clear();
        localDeletedPlayerIndices.clear();
        localVotesMap.clear();
        logs.setValue(new ArrayList<>());

        currentQuestion.setValue(questionRepository.getRandomQuestion());
        setPhase(Phase.WAITING_FOR_ANSWERS);
    }

    public void setPhase(Phase phase) {
        currentPhase.setValue(phase);
    }

    public void submitLocalAnswer(String answer) {
        localAnswers.set(currentPlayerIndex, answer);
        
        int nextPlayer = (currentPlayerIndex + 1) % players.getValue().size();
        if (nextPlayer == spotlightIndex) {
            // Everyone has answered
            prepareChoices();
            setPhase(Phase.REVIEW);
        } else {
            currentPlayerIndex = nextPlayer;
            setPhase(Phase.WAITING_FOR_ANSWERS);
        }
    }

    private void prepareChoices() {
        List<String> choices = new ArrayList<>();
        for (String ans : localAnswers) {
            if (ans != null) choices.add(ans);
        }
        Collections.shuffle(choices);
        currentChoices.setValue(choices);
    }

    public void toggleLocalMatch(int choiceIndex) {
        if (localMatchedPlayerIndices.contains(choiceIndex)) {
            localMatchedPlayerIndices.remove(choiceIndex);
        } else {
            localMatchedPlayerIndices.add(choiceIndex);
        }
    }

    public void deleteLocalChoice(int choiceIndex) {
        localDeletedPlayerIndices.add(choiceIndex);
        List<String> updatedChoices = new ArrayList<>(currentChoices.getValue());
        updatedChoices.set(choiceIndex, "--- DELETED ---");
        currentChoices.setValue(updatedChoices);
    }

    public void submitLocalVote(String choice) {
        localVotesMap.put(currentPlayerIndex, choice);
        
        int nextGuesser = (currentPlayerIndex + 1) % players.getValue().size();
        if (nextGuesser == spotlightIndex) {
            // Everyone has voted
            calculateLocalScores();
        } else {
            currentPlayerIndex = nextGuesser;
            setPhase(Phase.VOTING);
        }
    }

    public void calculateLocalScores() {
        List<Player> playerList = players.getValue();
        String spotlightAnswer = localAnswers.get(spotlightIndex);
        List<String> choices = currentChoices.getValue();

        // 1. Matched answers (Spotlight identified their own)
        for (int idx : localMatchedPlayerIndices) {
            String matchedAnswer = choices.get(idx);
            for (int i = 0; i < localAnswers.size(); i++) {
                if (i != spotlightIndex && matchedAnswer.equals(localAnswers.get(i))) {
                    // Spotlight matched someone else's answer - Spotlight gets 2, Player gets 0
                    playerList.get(spotlightIndex).addScore(2);
                    addLog(playerList.get(spotlightIndex).getName() + " matched " + playerList.get(i).getName() + "'s answer (+2)");
                }
            }
        }

        // 2. Voting results
        for (Map.Entry<Integer, String> entry : localVotesMap.entrySet()) {
            int voterIdx = entry.getKey();
            String votedAnswer = entry.getValue();

            if (votedAnswer.equals(spotlightAnswer)) {
                // Guessed spotlight correctly
                playerList.get(voterIdx).addScore(2);
                playerList.get(spotlightIndex).addScore(1);
                addLog(playerList.get(voterIdx).getName() + " correctly guessed " + playerList.get(spotlightIndex).getName() + "'s answer (+2 to guesser, +1 to spotlight)");
            } else {
                // Guessed someone else
                for (int i = 0; i < localAnswers.size(); i++) {
                    if (i != spotlightIndex && i != voterIdx && votedAnswer.equals(localAnswers.get(i))) {
                        playerList.get(i).addScore(1);
                        addLog(playerList.get(voterIdx).getName() + " guessed " + playerList.get(i).getName() + "'s answer (+1 to " + playerList.get(i).getName() + ")");
                    }
                }
            }
        }

        players.setValue(playerList);
        setPhase(Phase.RESULTS);
    }

    private void addLog(String message) {
        List<String> currentLogs = new ArrayList<>(logs.getValue());
        currentLogs.add(message);
        logs.setValue(currentLogs);
    }

    public void startVotingLocal() {
        currentPlayerIndex = (spotlightIndex + 1) % players.getValue().size();
        setPhase(Phase.VOTING);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        gameRepository.cleanup();
    }

    public boolean isHost() {
        if (isMultiplayer) {
            return playerId != null && playerId.equals(hostId);
        }
        return true;
    }

    public String getPlayerId() {
        return playerId;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public List<String> getLocalAnswers() {
        return localAnswers;
    }

    public Set<Integer> getLocalMatchedPlayerIndices() {
        return localMatchedPlayerIndices;
    }

    public Set<Integer> getLocalDeletedPlayerIndices() {
        return localDeletedPlayerIndices;
    }

    public Map<Integer, String> getLocalVotesMap() {
        return localVotesMap;
    }
}
