package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.logic.GameViewModel.Phase;
import com.spotlight.model.RoomStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiplayerGameSession implements GameSession {

    private final GameRepository gameRepository;
    private final QuestionRepository questionRepository;
    private String roomCode;
    private String playerId;
    private String hostId;

    // --- State Observables (LiveData) ---
    private final MutableLiveData<Phase> currentPhase = new MutableLiveData<>();
    private final MutableLiveData<Question> currentQuestion = new MutableLiveData<>();
    private final MutableLiveData<List<Player>> players = new MutableLiveData<>();
    private final MutableLiveData<Integer> spotlightPlayerIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentPlayerIndexLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> logs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> currentChoices = new MutableLiveData<>(new ArrayList<>());

    private Observer<GameRoom> roomObserver;

    public MultiplayerGameSession(GameRepository gameRepository, QuestionRepository questionRepository) {
        this.gameRepository = gameRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public void init(String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category) {
        this.roomCode = roomCode;
        this.playerId = playerId;
        this.hostId = hostId;
        this.players.setValue(initialPlayers);

        roomObserver = room -> {
            if (room != null) {

                if (isHost() && room.getSpotlightPlayerId() == null && room.getStatusEnum() != RoomStatus.WAITING) {
                    startNextRound();
                    return; // Stop here and wait for Firebase to return the initialized data
                }

                syncStateFromRoom(room);
            }
        };
        gameRepository.getRoomData(roomCode).observeForever(roomObserver);
    }

    private void syncStateFromRoom(GameRoom room) {
        if (room.getPlayers() != null) {
            this.players.setValue(new ArrayList<>(room.getPlayers().values()));
        }
        if (room.getLogs() != null) {
            this.logs.setValue(room.getLogs());
        }

        RoomStatus status = room.getStatusEnum();
        if (status != null) {
            Phase phase = Phase.WAITING_FOR_ANSWERS;

            if (status == RoomStatus.REVIEW) phase = Phase.REVIEW;
            else if (status == RoomStatus.VOTING) phase = Phase.VOTING;
            else if (status == RoomStatus.RESULTS) phase = Phase.RESULTS;
            else if (status == RoomStatus.FINISHED) phase = Phase.FINISHED;

            currentPhase.setValue(phase);
        }

        String spotlightId = room.getSpotlightPlayerId();
        if (spotlightId != null && room.getPlayers() != null) {
            if (room.getCurrentQuestion() != null) {
                currentQuestion.setValue(new Question(room.getCurrentQuestion(), ""));
            }

            List<Player> playerList = new ArrayList<>(room.getPlayers().values());
            for (int i = 0; i < playerList.size(); i++) {
                if (playerList.get(i).getId().equals(spotlightId)) {
                    spotlightPlayerIndex.setValue(i);
                    break;
                }
            }
        }
    }

    @Override
    public void startNextRound() {
        if (!isHost()) return;

        gameRepository.getRoomDataOnce(roomCode, room -> {
            if (room == null) return;

            Map<String, Player> playerMap = room.getPlayers();
            if (playerMap == null || playerMap.isEmpty()) return;

            List<Player> playerList = new ArrayList<>(playerMap.values());
            Collections.sort(playerList, (p1, p2) -> Long.compare(p1.getJoinTimestamp(), p2.getJoinTimestamp()));

            String currentSpotlightId = room.getSpotlightPlayerId();
            int currentSortedIndex = -1;
            if (currentSpotlightId != null) {
                for (int i = 0; i < playerList.size(); i++) {
                    if (playerList.get(i).getId().equals(currentSpotlightId)) {
                        currentSortedIndex = i;
                        break;
                    }
                }
            }

            int nextSortedIndex = (currentSortedIndex + 1) % playerList.size();
            String nextSpotlightId = playerList.get(nextSortedIndex).getId();
            Question nextQuestion = questionRepository.getRandomQuestion();

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", RoomStatus.WAITING_FOR_ANSWERS.name());
            updates.put("spotlightPlayerId", nextSpotlightId);
            updates.put("currentQuestion", nextQuestion.getText());
            updates.put("guesses", new HashMap<>());
            updates.put("votes", new HashMap<>());
            updates.put("players", playerMap);

            gameRepository.updateRoom(updates);
        });
    }

    @Override
    public void calculateScores(Set<String> matchedAnswers) {
        gameRepository.getRoomDataOnce(roomCode, room -> {
            if (room == null) return;

            String spotlightId = room.getSpotlightPlayerId();
            if (!isHost() && (playerId == null || !playerId.equals(spotlightId))) return;

            Map<String, Player> playerMap = room.getPlayers();
            Map<String, String> guesses = room.getGuesses();
            Map<String, String> votes = room.getVotes();
            String spotlightAnswer = guesses != null ? guesses.get(spotlightId) : null;

            Map<String, Integer> pointsAwarded = new HashMap<>();
            List<String> roundLogs = new ArrayList<>();

            java.util.function.BiConsumer<String, Integer> addPoints = (pId, pts) ->
                    pointsAwarded.put(pId, pointsAwarded.getOrDefault(pId, 0) + pts);

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

            if (votes != null && spotlightAnswer != null && guesses != null) {
                for (Map.Entry<String, String> entry : votes.entrySet()) {
                    String voterId = entry.getKey();
                    String votedAnswer = entry.getValue();

                    if (votedAnswer.equals(spotlightAnswer)) {
                        if (playerMap.containsKey(voterId)) addPoints.accept(voterId, 2);
                        if (playerMap.containsKey(spotlightId)) addPoints.accept(spotlightId, 1);
                        roundLogs.add(playerMap.get(voterId).getName() + " correctly guessed the Spotlight's answer!");
                    } else {
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

            boolean gameCompleted = false;
            for (Map.Entry<String, Player> entry : playerMap.entrySet()) {
                int currentScore = entry.getValue().getScore();
                int addedScore = pointsAwarded.getOrDefault(entry.getKey(), 0);
                if ((currentScore + addedScore) >= 25) {
                    gameCompleted = true;
                    break;
                }
            }

            gameRepository.finalizeRoundTransaction(roomCode, pointsAwarded, roundLogs, gameCompleted);
        });
    }

    @Override
    public void submitAnswer(String answer) {
        if (playerId != null) gameRepository.submitAnswer(playerId, answer);
    }

    @Override
    public void submitVote(String vote) {
        if (playerId != null) gameRepository.submitVote(playerId, vote);
    }

    @Override
    public void updateStatus(RoomStatus status) {
        gameRepository.updateRoomStatus(status);
    }

    @Override
    public void leaveGame() {
        if (roomCode != null && playerId != null) {
            gameRepository.removePlayer(playerId);
        }
    }

    @Override
    public void cleanup() {
        if (roomObserver != null) {
            gameRepository.getRoomData(roomCode).removeObserver(roomObserver);
        }
    }

    // --- Local-Only Actions (No-ops for Multiplayer) ---
    @Override public void toggleMatch(String answer) {}
    @Override public void deleteChoice(int choiceIndex) {}

    // --- Getters ---
    @Override public LiveData<Phase> getCurrentPhase() { return currentPhase; }
    @Override public LiveData<Question> getCurrentQuestion() { return currentQuestion; }
    @Override public LiveData<List<Player>> getPlayers() { return players; }
    @Override public LiveData<Integer> getSpotlightPlayerIndex() { return spotlightPlayerIndex; }
    @Override public LiveData<Integer> getCurrentPlayerIndex() { return currentPlayerIndexLiveData; }
    @Override public LiveData<List<String>> getCurrentChoices() { return currentChoices; }
    @Override public LiveData<List<String>> getLogs() { return logs; }

    @Override public boolean isHost() { return playerId != null && playerId.equals(hostId); }
    @Override public Set<String> getMatchedAnswers() { return new HashSet<>(); }
    @Override public Set<Integer> getDeletedChoiceIndices() { return new HashSet<>(); }
    @Override public List<String> getLocalAnswers() { return new ArrayList<>(); }
}