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

    public void setPlayers(List<Player> playerList) {
        this.players.setValue(playerList);
    }

    public void setCurrentQuestion(String questionText) {
        this.currentQuestion.setValue(new Question(questionText, ""));
    }

    public void setSpotlightPlayerId(String spotlightId) {
        List<Player> playerList = players.getValue();
        if (playerList != null && spotlightId != null) {
            for (int i = 0; i < playerList.size(); i++) {
                if (playerList.get(i).getId().equals(spotlightId)) {
                    spotlightPlayerIndex.setValue(i);
                    break;
                }
            }
        }
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
        for (int i = 0; i < localAnswers.size(); i++) {
            if (i != spotlightIndex && localAnswers.get(i) != null) {
                choices.add(localAnswers.get(i));
            }
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

    public void startVotingLocal() {
        currentPlayerIndex = (spotlightIndex + 1) % players.getValue().size();
        setPhase(Phase.VOTING);
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

    public void startNextMultiplayerRound() {
        if (!isMultiplayer) return;

        gameRepository.getRoomDataOnce(roomCode, room -> {
            if (room == null) return;

            Map<String, Player> playerMap = room.getPlayers();
            if (playerMap == null || playerMap.isEmpty()) return;

            List<Player> playerList = new ArrayList<>(playerMap.values());

            // Sort players by join timestamp in descending order (latest to host)
            List<Player> sortedPlayers = new ArrayList<>(playerList);
            Collections.sort(sortedPlayers, (p1, p2) -> Long.compare(p2.getJoinTimestamp(), p1.getJoinTimestamp()));

            String currentSpotlightId = room.getSpotlightPlayerId();

            int currentSortedIndex = -1;
            if (currentSpotlightId != null) {
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    if (sortedPlayers.get(i).getId().equals(currentSpotlightId)) {
                        currentSortedIndex = i;
                        break;
                    }
                }
            }

            int nextSortedIndex = (currentSortedIndex + 1) % sortedPlayers.size();
            String nextSpotlightId = sortedPlayers.get(nextSortedIndex).getId();
            Question nextQuestion = questionRepository.getRandomQuestion();

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "WAITING_FOR_ANSWERS");
            updates.put("spotlightPlayerId", nextSpotlightId);
            updates.put("currentQuestion", nextQuestion.getText());
            updates.put("guesses", null);
            updates.put("votes", null);
            updates.put("players", playerMap);

            gameRepository.updateRoom(updates);
        });
    }

    public void calculateMultiplayerScores(Set<String> matchedAnswers) {
        gameRepository.getRoomDataOnce(roomCode, new GameRepository.OnRoomDataListener() {
            @Override
            public void onDataChange(GameRoom room) {
                if (room == null) return;

                String spotlightId = room.getSpotlightPlayerId();
                if (playerId == null || !playerId.equals(spotlightId)) return;

                Map<String, Player> playerMap = room.getPlayers();
                Map<String, String> guesses = room.getGuesses();
                Map<String, String> votes = room.getVotes();
                String spotlightAnswer = guesses.get(spotlightId);
                List<String> logsList = new ArrayList<>();

                // 1. Handle matches from Spotlight
                if (matchedAnswers != null && !matchedAnswers.isEmpty()) {
                    for (String matchedAnswer : matchedAnswers) {
                        for (Map.Entry<String, String> entry : guesses.entrySet()) {
                            String pId = entry.getKey();
                            if (!pId.equals(spotlightId) && matchedAnswer.equals(entry.getValue())) {
                                if (playerMap.containsKey(pId)) {
                                    playerMap.get(pId).addScore(4);
                                    playerMap.get(spotlightId).addScore(2);
                                    logsList.add(playerMap.get(pId).getName() + " matched the Spotlight's answer in Review! +4 (Spotlight +2)");
                                }
                            }
                        }
                    }
                }

                // 2. Handle votes
                if (votes != null) {
                    for (Map.Entry<String, String> entry : votes.entrySet()) {
                        String voterId = entry.getKey();
                        String votedAnswer = entry.getValue();

                        if (votedAnswer.equals(spotlightAnswer)) {
                            playerMap.get(voterId).addScore(2);
                            playerMap.get(spotlightId).addScore(1);
                            logsList.add(playerMap.get(voterId).getName() + " correctly guessed " + playerMap.get(spotlightId).getName() + "'s answer (+2 to guesser, +1 to spotlight)");
                        } else {
                            for (Map.Entry<String, String> guessEntry : guesses.entrySet()) {
                                String targetId = guessEntry.getKey();
                                if (!targetId.equals(spotlightId) && !targetId.equals(voterId) && votedAnswer.equals(guessEntry.getValue())) {
                                    playerMap.get(targetId).addScore(1);
                                    logsList.add(playerMap.get(voterId).getName() + " guessed " + playerMap.get(targetId).getName() + "'s answer (+1 to " + playerMap.get(targetId).getName() + ")");
                                }
                            }
                        }
                    }
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("players", playerMap);
                updates.put("status", "RESULTS");
                updates.put("logs", logsList);
                gameRepository.updateRoom(updates);
            }
        });
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
