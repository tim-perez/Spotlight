package com.spotlight.logic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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

public class LocalGameSession implements GameSession {

    private final QuestionRepository questionRepository;

    // --- State Observables (LiveData) ---
    private final MutableLiveData<Phase> currentPhase = new MutableLiveData<>();
    private final MutableLiveData<Question> currentQuestion = new MutableLiveData<>();
    private final MutableLiveData<List<Player>> players = new MutableLiveData<>();
    private final MutableLiveData<Integer> spotlightPlayerIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentPlayerIndexLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> logs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> currentChoices = new MutableLiveData<>(new ArrayList<>());

    // --- Internal Local State ---
    private int spotlightIndex = 0;
    private int currentPlayerIndex = 0;
    private final List<String> localAnswers = new ArrayList<>();
    private final Set<String> localMatchedAnswers = new HashSet<>();
    private final Set<Integer> localDeletedPlayerIndices = new HashSet<>();
    private final Map<Integer, String> localVotesMap = new HashMap<>();

    public LocalGameSession(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Override
    public void init(String roomCode, String playerId, String hostId, List<Player> initialPlayers, String category) {
        this.players.setValue(initialPlayers);
        // Note: The async loading wait is now handled by GameViewModel before init() is called
        if (category != null && !category.equals("All")) {
            questionRepository.filterByCategory(category);
        }
        startNextRound();
    }

    @Override
    public void startNextRound() {
        if (currentPhase.getValue() != null && currentPhase.getValue() != Phase.FINISHED) {
            spotlightIndex = (spotlightIndex + 1) % players.getValue().size();
            spotlightPlayerIndex.setValue(spotlightIndex);
        } else {
            spotlightIndex = 0;
            spotlightPlayerIndex.setValue(0);
            logs.setValue(new ArrayList<>());
        }

        currentPlayerIndex = spotlightIndex;
        currentPlayerIndexLiveData.setValue(currentPlayerIndex);
        localAnswers.clear();
        for (int i = 0; i < players.getValue().size(); i++) localAnswers.add(null);
        localMatchedAnswers.clear();
        localDeletedPlayerIndices.clear();
        localVotesMap.clear();

        currentQuestion.setValue(questionRepository.getRandomQuestion());
        currentPhase.setValue(Phase.WAITING_FOR_ANSWERS);
    }

    @Override
    public void submitAnswer(String answer) {
        localAnswers.set(currentPlayerIndex, answer);

        int nextPlayer = (currentPlayerIndex + 1) % players.getValue().size();
        if (nextPlayer == spotlightIndex) {
            prepareChoices();
            currentPhase.setValue(Phase.REVIEW);
        } else {
            currentPlayerIndex = nextPlayer;
            currentPlayerIndexLiveData.setValue(currentPlayerIndex);
            currentPhase.setValue(Phase.WAITING_FOR_ANSWERS);
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

    @Override
    public void toggleMatch(String answer) {
        if (localMatchedAnswers.contains(answer)) {
            localMatchedAnswers.remove(answer);
        } else {
            localMatchedAnswers.add(answer);
        }
    }

    @Override
    public void deleteChoice(int choiceIndex) {
        localDeletedPlayerIndices.add(choiceIndex);
        List<String> updatedChoices = new ArrayList<>(currentChoices.getValue());
        updatedChoices.set(choiceIndex, "--- DELETED ---");
        currentChoices.setValue(updatedChoices);
    }

    @Override
    public void updateStatus(RoomStatus status) {
        // In local mode, updateStatus is used to trigger the Voting phase manually from the UI
        if (status == RoomStatus.VOTING) {
            currentPlayerIndex = (spotlightIndex + 1) % players.getValue().size();
            currentPlayerIndexLiveData.setValue(currentPlayerIndex);
            currentPhase.setValue(Phase.VOTING);
        }
    }

    @Override
    public void submitVote(String choice) {
        localVotesMap.put(currentPlayerIndex, choice);

        int nextGuesser = (currentPlayerIndex + 1) % players.getValue().size();
        if (nextGuesser == spotlightIndex) {
            calculateScores(null); // Local mode uses internal localMatchedAnswers
        } else {
            currentPlayerIndex = nextGuesser;
            currentPlayerIndexLiveData.setValue(currentPlayerIndex);
            currentPhase.setValue(Phase.VOTING);
        }
    }

    @Override
    public void calculateScores(Set<String> dummyMatchedAnswers) {
        List<Player> playerList = players.getValue();
        if (playerList == null) return;

        int spotlightIdx = spotlightPlayerIndex.getValue();
        String spotlightAnswer = localAnswers.get(spotlightIdx);

        if (!localMatchedAnswers.isEmpty()) {
            for (String matchedAnswer : localMatchedAnswers) {
                for (int i = 0; i < localAnswers.size(); i++) {
                    if (i != spotlightIdx && matchedAnswer.equalsIgnoreCase(localAnswers.get(i))) {
                        playerList.get(i).addScore(4);
                        addLog(playerList.get(i).getName() + " matched the Spotlight's answer! (+4)");
                    }
                }
            }
            players.setValue(playerList);
            currentPhase.setValue(Phase.RESULTS);
            return;
        }

        for (Map.Entry<Integer, String> entry : localVotesMap.entrySet()) {
            int voterIdx = entry.getKey();
            String votedAnswer = entry.getValue();

            if (votedAnswer.equals(spotlightAnswer)) {
                playerList.get(voterIdx).addScore(2);
                playerList.get(spotlightIdx).addScore(1);
                addLog(playerList.get(voterIdx).getName() + " correctly guessed the Spotlight's answer!");
            } else {
                for (int i = 0; i < localAnswers.size(); i++) {
                    if (i != spotlightIdx && i != voterIdx && votedAnswer.equals(localAnswers.get(i))) {
                        playerList.get(i).addScore(1);
                        addLog(playerList.get(i).getName() + " was guessed by " + playerList.get(voterIdx).getName() + "! (+1)");
                    }
                }
            }
        }

        players.setValue(playerList);

        boolean gameCompleted = false;
        for (Player p : playerList) {
            if (p.getScore() >= 25) {
                gameCompleted = true;
                break;
            }
        }

        currentPhase.setValue(gameCompleted ? Phase.FINISHED : Phase.RESULTS);
    }

    private void addLog(String message) {
        List<String> currentLogs = new ArrayList<>(logs.getValue());
        currentLogs.add(message);
        logs.setValue(currentLogs);
    }

    @Override
    public void leaveGame() {
        // Nothing to disconnect in local mode
    }

    @Override
    public void cleanup() {
        // Nothing to clean up
    }

    // --- Getters ---
    @Override public LiveData<Phase> getCurrentPhase() { return currentPhase; }
    @Override public LiveData<Question> getCurrentQuestion() { return currentQuestion; }
    @Override public LiveData<List<Player>> getPlayers() { return players; }
    @Override public LiveData<Integer> getSpotlightPlayerIndex() { return spotlightPlayerIndex; }
    @Override public LiveData<Integer> getCurrentPlayerIndex() { return currentPlayerIndexLiveData; }
    @Override public LiveData<List<String>> getCurrentChoices() { return currentChoices; }
    @Override public LiveData<List<String>> getLogs() { return logs; }

    @Override public boolean isHost() { return true; } // Always host in local mode
    @Override public Set<String> getMatchedAnswers() { return localMatchedAnswers; }
    @Override public Set<Integer> getDeletedChoiceIndices() { return localDeletedPlayerIndices; }
    @Override public List<String> getLocalAnswers() { return localAnswers; }
}