package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

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
    
    private final MutableLiveData<Phase> currentPhase = new MutableLiveData<>();
    private final MutableLiveData<Question> currentQuestion = new MutableLiveData<>();
    private final MutableLiveData<List<Player>> players = new MutableLiveData<>();
    private final MutableLiveData<Integer> spotlightPlayerIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentPlayerIndexLiveData = new MutableLiveData<>(0);
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
    private final Set<String> localMatchedAnswers = new HashSet<>();
    private final Set<Integer> localDeletedPlayerIndices = new HashSet<>();
    private final Map<Integer, String> localVotesMap = new HashMap<>();

    public GameViewModel(GameRepository gameRepository, QuestionRepository questionRepository) {
        this.gameRepository = gameRepository;
        this.questionRepository = questionRepository;
    }

    public void init(boolean isMultiplayer, String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category) {
        this.isMultiplayer = isMultiplayer;
        this.roomCode = roomCode;
        this.playerId = playerId;
        this.hostId = hostId;
        this.players.setValue(initialPlayers);

        questionRepository.loadQuestionsAsync(() -> {

            if (category != null && !category.equals("All")) {
                questionRepository.filterByCategory(category);
            }

            if (this.isMultiplayer) {
                setupMultiplayer();
            } else {
                startNewRoundLocal();
            }

        });
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

    public LiveData<Integer> getCurrentPlayerIndexLiveData() {
        return currentPlayerIndexLiveData;
    }

    public void startNewRoundLocal() {
        if (currentPhase.getValue() != null && currentPhase.getValue() != Phase.FINISHED) {
            spotlightIndex = (spotlightIndex + 1) % players.getValue().size();
            spotlightPlayerIndex.setValue(spotlightIndex);
        } else {
            // Start with the first player (the one who created/setup the game)
            spotlightIndex = 0;
            spotlightPlayerIndex.setValue(0);
            logs.setValue(new ArrayList<>());
        }
        
        // Answering rotation starts with the Spotlight player
        currentPlayerIndex = spotlightIndex;
        currentPlayerIndexLiveData.setValue(currentPlayerIndex);
        localAnswers.clear();
        for (int i = 0; i < players.getValue().size(); i++) localAnswers.add(null);
        localMatchedAnswers.clear();
        localDeletedPlayerIndices.clear();
        localVotesMap.clear();

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

    public void setLogs(List<String> logsList) {
        this.logs.setValue(logsList);
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
        // The rotation is complete when it reaches back to the player who started (the Spotlight)
        if (nextPlayer == spotlightIndex) {
            // Everyone has answered
            prepareChoices();
            setPhase(Phase.REVIEW);
        } else {
            currentPlayerIndex = nextPlayer;
            currentPlayerIndexLiveData.setValue(currentPlayerIndex);
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

    public void toggleLocalMatch(String answer) {
        if (localMatchedAnswers.contains(answer)) {
            localMatchedAnswers.remove(answer);
        } else {
            localMatchedAnswers.add(answer);
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
        currentPlayerIndexLiveData.setValue(currentPlayerIndex);
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
            currentPlayerIndexLiveData.setValue(currentPlayerIndex);
            setPhase(Phase.VOTING);
        }
    }

    public void calculateLocalScores() {
        List<Player> playerList = players.getValue();
        if (playerList == null) return;
        
        int spotlightIdx = spotlightPlayerIndex.getValue();
        String spotlightAnswer = localAnswers.get(spotlightIdx);

        // 1. Matched answers (Spotlight identified their own answer among others)
        // If the spotlight player detects one of the answers matches their answer,
        // the player who matched gets +4, while everyone else receives +0.
        if (!localMatchedAnswers.isEmpty()) {
            for (String matchedAnswer : localMatchedAnswers) {
                for (int i = 0; i < localAnswers.size(); i++) {
                    if (i != spotlightIdx && matchedAnswer.equalsIgnoreCase(localAnswers.get(i))) {
                        playerList.get(i).addScore(4);
                        addLog(playerList.get(i).getName() + " matched the Spotlight's answer! (+4)");
                    }
                }
            }
            // Round ends immediately if matches are found
            players.setValue(playerList);
            setPhase(Phase.RESULTS);
            return;
        }

        // 2. Voting results
        for (Map.Entry<Integer, String> entry : localVotesMap.entrySet()) {
            int voterIdx = entry.getKey();
            String votedAnswer = entry.getValue();

            if (votedAnswer.equals(spotlightAnswer)) {
                // Guessed spotlight correctly
                playerList.get(voterIdx).addScore(2);
                playerList.get(spotlightIdx).addScore(1);
                addLog(playerList.get(voterIdx).getName() + " correctly guessed the Spotlight's answer! (+2 for guesser, +1 for Spotlight)");
            } else {
                // Guessed someone else
                for (int i = 0; i < localAnswers.size(); i++) {
                    if (i != spotlightIdx && i != voterIdx && votedAnswer.equals(localAnswers.get(i))) {
                        playerList.get(i).addScore(1);
                        addLog(playerList.get(i).getName() + " was guessed by " + playerList.get(voterIdx).getName() + "! (+1)");
                    }
                }
            }
        }

        players.setValue(playerList);

        // Check if the game is completed (First to 25 points wins)
        boolean gameCompleted = false;
        for (Player p : playerList) {
            if (p.getScore() >= 25) {
                gameCompleted = true;
                break;
            }
        }

        if (gameCompleted) {
            setPhase(Phase.FINISHED);
        } else {
            setPhase(Phase.RESULTS);
        }
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

            // Sort players by join timestamp in ascending order (host first, then others)
            List<Player> sortedPlayers = new ArrayList<>(playerList);
            Collections.sort(sortedPlayers, (p1, p2) -> Long.compare(p1.getJoinTimestamp(), p2.getJoinTimestamp()));

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
            updates.put("guesses", new HashMap<>());
            updates.put("votes", new HashMap<>());
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
                if (!isHost() && (playerId == null || !playerId.equals(spotlightId))) return;

                Map<String, Player> playerMap = room.getPlayers();
                Map<String, String> guesses = room.getGuesses();
                Map<String, String> votes = room.getVotes();
                String spotlightAnswer = guesses != null ? guesses.get(spotlightId) : null;

                // Track points to award and logs to generate for the Transaction
                Map<String, Integer> pointsAwarded = new HashMap<>();
                List<String> roundLogs = new ArrayList<>();

                // Helper to safely increment the points map
                java.util.function.BiConsumer<String, Integer> addPoints = (pId, pts) ->
                        pointsAwarded.put(pId, pointsAwarded.getOrDefault(pId, 0) + pts);

                // 1. Handle matches from Spotlight (Review Phase)
                if (matchedAnswers != null && !matchedAnswers.isEmpty() && guesses != null) {
                    for (String matchedAnswer : matchedAnswers) {
                        for (Map.Entry<String, String> entry : guesses.entrySet()) {
                            String pId = entry.getKey();
                            if (!pId.equals(spotlightId) && matchedAnswer.equalsIgnoreCase(entry.getValue())) {
                                if (playerMap.containsKey(pId)) {
                                    addPoints.accept(pId, 4);
                                    roundLogs.add(playerMap.get(pId).getName() + " matched the Spotlight's answer! (+4)");
                                }
                            }
                        }
                    }
                }

                // 2. Handle votes (Voting Phase)
                if (votes != null && spotlightAnswer != null && guesses != null) {
                    for (Map.Entry<String, String> entry : votes.entrySet()) {
                        String voterId = entry.getKey();
                        String votedAnswer = entry.getValue();

                        if (votedAnswer.equals(spotlightAnswer)) {
                            // Correct guess: +2 to guesser, +1 to Spotlight
                            if (playerMap.containsKey(voterId)) addPoints.accept(voterId, 2);
                            if (playerMap.containsKey(spotlightId)) addPoints.accept(spotlightId, 1);
                            roundLogs.add(playerMap.get(voterId).getName() + " correctly guessed the Spotlight's answer!");
                        } else {
                            // Guessed someone else: +1 to the person guessed
                            for (Map.Entry<String, String> guessEntry : guesses.entrySet()) {
                                String targetId = guessEntry.getKey();
                                if (!targetId.equals(spotlightId) && !targetId.equals(voterId) && votedAnswer.equals(guessEntry.getValue())) {
                                    if (playerMap.containsKey(targetId)) {
                                        addPoints.accept(targetId, 1);
                                        roundLogs.add(playerMap.get(targetId).getName() + " was guessed by " + playerMap.get(voterId).getName() + "! (+1)");
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Determine if game is finished by checking current scores + new points
                boolean gameCompleted = false;
                for (Map.Entry<String, Player> entry : playerMap.entrySet()) {
                    int currentScore = entry.getValue().getScore();
                    int addedScore = pointsAwarded.getOrDefault(entry.getKey(), 0);
                    if ((currentScore + addedScore) >= 25) {
                        gameCompleted = true;
                        break;
                    }
                }

                // 4. Fire the atomic transaction
                gameRepository.finalizeRoundTransaction(roomCode, pointsAwarded, roundLogs, gameCompleted);
            }
        });
    }

    public void leaveMultiplayerRoom() {
        if (!isMultiplayer || roomCode == null || playerId == null) return;
        gameRepository.removePlayer(playerId);
    }

    public void submitMultiplayerAnswer(String answer) {
        if (isMultiplayer && playerId != null) {
            gameRepository.submitAnswer(playerId, answer);
        }
    }

    public void submitMultiplayerVote(String vote) {
        if (isMultiplayer && playerId != null) {
            gameRepository.submitVote(playerId, vote);
        }
    }

    public void updateMultiplayerStatus(String status) {
        if (isMultiplayer) {
            gameRepository.updateRoomStatus(status);
        }
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

    public Set<String> getLocalMatchedAnswers() {
        return localMatchedAnswers;
    }

    public Set<Integer> getLocalDeletedPlayerIndices() {
        return localDeletedPlayerIndices;
    }

    public Map<Integer, String> getLocalVotesMap() {
        return localVotesMap;
    }
}
