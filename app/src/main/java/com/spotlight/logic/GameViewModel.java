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
    private final MutableLiveData<Integer> currentPlayerIndexLiveData = new MutableLiveData<>(0);
    private final MutableLiveData<List<String>> logs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> currentChoices = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isWaitingForOthers = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showPassDevice = new MutableLiveData<>(false);
    private final MutableLiveData<String> spotlightPlayerName = new MutableLiveData<>("");
    private final MutableLiveData<Integer> spotlightAvatarColor = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> passToAvatarColor = new MutableLiveData<>(0);
    private final MutableLiveData<String> passToPlayerName = new MutableLiveData<>("");
    private final MutableLiveData<String> winnerName = new MutableLiveData<>("");
    private final MutableLiveData<List<Player>> sortedPlayers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> selectionPrompt = new MutableLiveData<>("");
    private final MutableLiveData<String> actionButtonText = new MutableLiveData<>("");
    private final MutableLiveData<String> phaseTitle = new MutableLiveData<>("");
    private final MutableLiveData<Set<String>> matchedAnswersLiveData = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Boolean> actionButtonEnabled = new MutableLiveData<>(false);

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

    // Multiplayer state
    private String selectedVote = null;

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
        // ViewModel will manage room data and derive its own state
    }

    public void processRoomUpdate(GameRoom room) {
        if (room == null) return;

        if (room.getPlayers() != null) {
            setPlayers(new ArrayList<>(room.getPlayers().values()));
        }

        if (room.getLogs() != null) {
            setLogs(room.getLogs());
        }

        String status = room.getStatus();
        Phase phase = Phase.WAITING_FOR_ANSWERS;
        if ("REVIEW".equals(status)) phase = Phase.REVIEW;
        else if ("VOTING".equals(status)) phase = Phase.VOTING;
        else if ("RESULTS".equals(status)) phase = Phase.RESULTS;
        else if ("FINISHED".equals(status)) phase = Phase.FINISHED;

        setPhase(phase);

        if (isMultiplayer && room != null) {
            if (phase == Phase.WAITING_FOR_ANSWERS) {
                boolean answered = room.getGuesses() != null && room.getGuesses().containsKey(playerId);
                isWaitingForOthers.setValue(answered);
            } else if (phase == Phase.VOTING) {
                boolean voted = room.getVotes() != null && room.getVotes().containsKey(playerId);
                isWaitingForOthers.setValue(voted);
            } else {
                isWaitingForOthers.setValue(false);
            }
        }

        // Auto-transition logic for Host
        if (isHost()) {
            if (phase == Phase.WAITING_FOR_ANSWERS) {
                Map<String, String> guesses = room.getGuesses();
                if (guesses != null && guesses.size() == room.getPlayers().size() && !room.getPlayers().isEmpty()) {
                    gameRepository.updateRoomStatus("REVIEW");
                }
            } else if (phase == Phase.VOTING) {
                Map<String, String> votes = room.getVotes();
                Map<String, Player> playersMap = room.getPlayers();
                if (votes != null && votes.size() == playersMap.size() - 1 && playersMap.size() > 1) {
                    calculateMultiplayerScores(null);
                }
            }
        }

        String spotlightId = room.getSpotlightPlayerId();
        if (spotlightId != null) {
            setSpotlightPlayerId(spotlightId);
            if (room.getCurrentQuestion() != null) {
                currentQuestion.setValue(new Question(room.getCurrentQuestion(), ""));
            }
        }

        if (phase == Phase.REVIEW || phase == Phase.VOTING) {
            prepareMultiplayerChoices(room, phase);
        }
    }

    private void prepareMultiplayerChoices(GameRoom room, Phase phase) {
        Map<String, String> guesses = room.getGuesses();
        if (guesses == null) return;

        String spotlightId = room.getSpotlightPlayerId();
        List<String> choices = new ArrayList<>();

        if (phase == Phase.REVIEW) {
            for (Map.Entry<String, String> entry : guesses.entrySet()) {
                if (!entry.getKey().equals(spotlightId)) {
                    choices.add(entry.getValue());
                }
            }
        } else if (phase == Phase.VOTING) {
            if (playerId != null && !playerId.equals(spotlightId)) {
                String myAnswer = guesses.get(playerId);
                for (Map.Entry<String, String> entry : guesses.entrySet()) {
                    if (!entry.getKey().equals(playerId)) {
                        choices.add(entry.getValue());
                    }
                }
            }
        }

        Collections.shuffle(choices);
        currentChoices.setValue(choices);
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

    public String getSelectedVote() {
        return selectedVote;
    }

    public void setSelectedVote(String vote) {
        this.selectedVote = vote;
        updateUIStrings();
    }

    private void confirmVote() {
        if (selectedVote == null) return;
        if (isMultiplayer) {
            if (playerId != null) {
                gameRepository.submitVote(playerId, selectedVote);
            }
        } else {
            submitLocalVote(selectedVote);
        }
        selectedVote = null;
    }

    public boolean isSpotlight() {
        if (isMultiplayer) {
            List<Player> playerList = players.getValue();
            Integer spotlightIdx = spotlightPlayerIndex.getValue();
            if (playerList == null || spotlightIdx == null || spotlightIdx < 0 || spotlightIdx >= playerList.size()) return false;
            Player spotlight = playerList.get(spotlightIdx);
            return playerId != null && playerId.equals(spotlight.getId());
        } else {
            return currentPlayerIndex == spotlightIndex;
        }
    }

    public void handleAction() {
        Phase phase = currentPhase.getValue();
        if (phase == Phase.RESULTS) {
            if (isMultiplayer) {
                if (isHost()) startNextMultiplayerRound();
            } else {
                startNewRoundLocal();
            }
        } else if (phase == Phase.FINISHED) {
            // Activity should finish
        } else if (phase == Phase.REVIEW) {
            Set<String> matched = matchedAnswersLiveData.getValue();
            if (isMultiplayer) {
                moveToVotingPhase(matched);
                matchedAnswersLiveData.setValue(new HashSet<>());
            } else {
                if ((matched != null && !matched.isEmpty()) || !localDeletedPlayerIndices.isEmpty()) {
                    calculateLocalScores();
                } else {
                    startVotingLocal();
                }
            }
        } else if (phase == Phase.VOTING) {
            confirmVote();
        }
    }

    public void moveToVotingPhase(Set<String> matchedAnswers) {
        if (isMultiplayer) {
            if (matchedAnswers != null && !matchedAnswers.isEmpty()) {
                calculateMultiplayerScores(matchedAnswers);
            } else {
                gameRepository.updateRoomStatus("VOTING");
            }
        }
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

    public LiveData<GameRoom> getRoomData() {
        return gameRepository.getRoomData(roomCode);
    }

    public LiveData<List<String>> getLogs() {
        return logs;
    }

    public LiveData<List<String>> getCurrentChoices() {
        return currentChoices;
    }

    public LiveData<Boolean> getIsWaitingForOthers() {
        return isWaitingForOthers;
    }

    public LiveData<Boolean> getShowPassDevice() {
        return showPassDevice;
    }

    public LiveData<String> getSpotlightPlayerName() {
        return spotlightPlayerName;
    }

    public LiveData<Integer> getSpotlightAvatarColor() {
        return spotlightAvatarColor;
    }

    public LiveData<Integer> getPassToAvatarColor() {
        return passToAvatarColor;
    }

    public LiveData<String> getPassToPlayerName() {
        return passToPlayerName;
    }

    public LiveData<String> getWinnerName() {
        return winnerName;
    }

    public LiveData<List<Player>> getSortedPlayers() {
        return sortedPlayers;
    }

    public LiveData<String> getSelectionPrompt() {
        return selectionPrompt;
    }

    public LiveData<String> getActionButtonText() {
        return actionButtonText;
    }

    public LiveData<String> getPhaseTitle() {
        return phaseTitle;
    }

    public LiveData<Set<String>> getMatchedAnswers() {
        return matchedAnswersLiveData;
    }

    public LiveData<Boolean> getActionButtonEnabled() {
        return actionButtonEnabled;
    }

    public void hidePassDevice() {
        showPassDevice.setValue(false);
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
        if (players.getValue() != null) {
            for (int i = 0; i < players.getValue().size(); i++) localAnswers.add(null);
        }
        matchedAnswersLiveData.setValue(new HashSet<>());
        localDeletedPlayerIndices.clear();
        localVotesMap.clear();

        currentQuestion.setValue(questionRepository.getRandomQuestion());
        updateSpotlightPlayerName();
        setPhase(Phase.WAITING_FOR_ANSWERS);
        showPassDevice.setValue(true);
    }

    private void updateUIStrings() {
        Phase phase = currentPhase.getValue();
        if (phase == null) return;

        boolean isSpotlight = isSpotlight();
        Set<String> matched = matchedAnswersLiveData.getValue();
        List<Player> playerList = players.getValue();

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                if (isMultiplayer) {
                    phaseTitle.setValue(isSpotlight ? "SPOTLIGHT" : "GUESSING");
                } else {
                    phaseTitle.setValue("ANSWERS");
                }
                selectionPrompt.setValue(""); // Not used in this phase
                break;

            case REVIEW:
                phaseTitle.setValue("REVIEW");
                if (isSpotlight) {
                    actionButtonText.setValue((matched == null || matched.isEmpty()) ? "START VOTING" : "REVEAL RESULTS");
                    actionButtonEnabled.setValue(true);
                    if (isMultiplayer) {
                        selectionPrompt.setValue("Review the answers. Match any that are the same as yours.");
                    } else {
                        String secret = (localAnswers != null && spotlightIndex < localAnswers.size()) ? localAnswers.get(spotlightIndex) : "";
                        selectionPrompt.setValue("Your answer: " + secret + "\nMatch any that are the same.");
                    }
                } else {
                    selectionPrompt.setValue("The Spotlight is reviewing answers...");
                }
                break;

            case VOTING:
                phaseTitle.setValue("VOTING");
                if (!isSpotlight) {
                    actionButtonText.setValue("CONFIRM VOTE");
                    actionButtonEnabled.setValue(selectedVote != null);
                    if (isMultiplayer) {
                        selectionPrompt.setValue("Pick the answer you think belongs to the Spotlight!");
                    } else {
                        String name = (playerList != null && currentPlayerIndex < playerList.size()) ? playerList.get(currentPlayerIndex).getName() : "";
                        selectionPrompt.setValue(name + ", pick the Spotlight's answer!");
                    }
                } else {
                    selectionPrompt.setValue("Everyone else is voting for your answer...");
                }
                break;

            case RESULTS:
                phaseTitle.setValue(isMultiplayer ? "ROUND RESULTS" : "RESULTS");
                if (isMultiplayer) {
                    actionButtonText.setValue(isHost() ? "NEXT ROUND" : "WAITING FOR HOST...");
                    actionButtonEnabled.setValue(isHost());
                } else {
                    actionButtonText.setValue("NEXT ROUND");
                    actionButtonEnabled.setValue(true);
                }
                break;

            case FINISHED:
                phaseTitle.setValue(isMultiplayer ? "GAME OVER" : "GAME OVER");
                actionButtonText.setValue("EXIT GAME");
                actionButtonEnabled.setValue(true);
                break;
        }
    }

    private void updateSpotlightPlayerName() {
        List<Player> playerList = players.getValue();
        Integer index = spotlightPlayerIndex.getValue();
        if (playerList != null && index != null && index >= 0 && index < playerList.size()) {
            spotlightPlayerName.setValue(playerList.get(index).getName());
            spotlightAvatarColor.setValue(playerList.get(index).getAvatarColor());
        }
    }

    private void updatePassToInfo() {
        List<Player> playerList = players.getValue();
        if (playerList != null && currentPlayerIndex >= 0 && currentPlayerIndex < playerList.size()) {
            passToPlayerName.setValue(playerList.get(currentPlayerIndex).getName());
            passToAvatarColor.setValue(playerList.get(currentPlayerIndex).getAvatarColor());
        }
    }

    private void updateSortedPlayers() {
        List<Player> playerList = players.getValue();
        if (playerList != null) {
            List<Player> sorted = new ArrayList<>(playerList);
            Collections.sort(sorted, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
            sortedPlayers.setValue(sorted);
        }
    }

    private void calculateWinner() {
        List<Player> playerList = players.getValue();
        if (playerList == null || playerList.isEmpty()) return;

        Player winner = playerList.get(0);
        for (Player p : playerList) {
            if (p.getScore() > winner.getScore()) {
                winner = p;
            }
        }
        winnerName.setValue(winner.getName());
    }

    public void setPhase(Phase phase) {
        currentPhase.setValue(phase);
        if (!isMultiplayer) {
            boolean shouldShowPass = phase != Phase.RESULTS && phase != Phase.FINISHED;
            showPassDevice.setValue(shouldShowPass);
            if (shouldShowPass) {
                updatePassToInfo();
            }
        }
        if (phase == Phase.RESULTS || phase == Phase.FINISHED) {
            updateSortedPlayers();
        }
        if (phase == Phase.FINISHED) {
            calculateWinner();
        }
        updateUIStrings();
    }

    public void setPlayers(List<Player> playerList) {
        this.players.setValue(playerList);
        updateSpotlightPlayerName();
        updateSortedPlayers();
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
                    updateSpotlightPlayerName();
                    break;
                }
            }
        }
    }

    public void submitAnswer(String answer) {
        if (isMultiplayer) {
            if (playerId != null) {
                gameRepository.submitAnswer(playerId, answer);
            }
        } else {
            submitLocalAnswer(answer);
        }
    }

    private void submitLocalAnswer(String answer) {
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
            updatePassToInfo();
            setPhase(Phase.WAITING_FOR_ANSWERS);
            showPassDevice.setValue(true);
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
        toggleMatch(answer);
    }

    public void deleteChoice(int choiceIndex) {
        if (!isMultiplayer) {
            localDeletedPlayerIndices.add(choiceIndex);
            List<String> updatedChoices = new ArrayList<>(currentChoices.getValue());
            updatedChoices.set(choiceIndex, "--- DELETED ---");
            currentChoices.setValue(updatedChoices);
        }
    }

    public void startVotingLocal() {
        currentPlayerIndex = (spotlightIndex + 1) % players.getValue().size();
        currentPlayerIndexLiveData.setValue(currentPlayerIndex);
        updateVotingChoicesLocal();
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
            updatePassToInfo();
            updateVotingChoicesLocal();
            setPhase(Phase.VOTING);
            showPassDevice.setValue(true);
        }
    }

    private void updateVotingChoicesLocal() {
        List<String> votingChoices = new ArrayList<>();
        for (int i = 0; i < localAnswers.size(); i++) {
            if (i != currentPlayerIndex && localAnswers.get(i) != null) {
                votingChoices.add(localAnswers.get(i));
            }
        }
        Collections.shuffle(votingChoices);
        currentChoices.setValue(votingChoices);
    }

    public void calculateLocalScores() {
        List<Player> playerList = players.getValue();
        if (playerList == null) return;
        
        int spotlightIdx = spotlightPlayerIndex.getValue();
        String spotlightAnswer = localAnswers.get(spotlightIdx);

        // 1. Matched answers (Spotlight identified their own answer among others)
        // If the spotlight player detects one of the answers matches their answer,
        // the player who matched gets +4, while everyone else receives +0.
        Set<String> matched = matchedAnswersLiveData.getValue();
        if (matched != null && !matched.isEmpty()) {
            for (String matchedAnswer : matched) {
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
                // Allow Host (for voting completion) or Spotlight (for review completion) to calculate
                if (!isHost() && (playerId == null || !playerId.equals(spotlightId))) return;

                Map<String, Player> playerMap = room.getPlayers();
                Map<String, String> guesses = room.getGuesses();
                Map<String, String> votes = room.getVotes();
                String spotlightAnswer = guesses != null ? guesses.get(spotlightId) : null;
                List<String> logsList = new ArrayList<>(room.getLogs());

                // 1. Handle matches from Spotlight (Review Phase)
                if (matchedAnswers != null && !matchedAnswers.isEmpty() && guesses != null) {
                    for (String matchedAnswer : matchedAnswers) {
                        for (Map.Entry<String, String> entry : guesses.entrySet()) {
                            String pId = entry.getKey();
                            if (!pId.equals(spotlightId) && matchedAnswer.equalsIgnoreCase(entry.getValue())) {
                                if (playerMap.containsKey(pId)) {
                                    playerMap.get(pId).addScore(4);
                                    logsList.add(playerMap.get(pId).getName() + " matched the Spotlight's answer! (+4)");
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
                            if (playerMap.containsKey(voterId)) playerMap.get(voterId).addScore(2);
                            if (playerMap.containsKey(spotlightId)) playerMap.get(spotlightId).addScore(1);
                            logsList.add(playerMap.get(voterId).getName() + " correctly guessed the Spotlight's answer! (+2 for guesser, +1 for Spotlight)");
                        } else {
                            // Guessed someone else: +1 to the person guessed
                            for (Map.Entry<String, String> guessEntry : guesses.entrySet()) {
                                String targetId = guessEntry.getKey();
                                if (!targetId.equals(spotlightId) && !targetId.equals(voterId) && votedAnswer.equals(guessEntry.getValue())) {
                                    if (playerMap.containsKey(targetId)) {
                                        playerMap.get(targetId).addScore(1);
                                        logsList.add(playerMap.get(targetId).getName() + " was guessed by " + playerMap.get(voterId).getName() + "! (+1)");
                                    }
                                }
                            }
                        }
                    }
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("players", playerMap);

                // Check if the game is completed (First to 25 points wins)
                boolean gameCompleted = false;
                for (Player p : playerMap.values()) {
                    if (p.getScore() >= 25) {
                        gameCompleted = true;
                        break;
                    }
                }

                updates.put("status", gameCompleted ? "FINISHED" : "RESULTS");
                updates.put("logs", logsList);
                gameRepository.updateRoom(updates);
            }
        });
    }

    public void leaveMultiplayerRoom() {
        if (!isMultiplayer || roomCode == null || playerId == null) return;
        gameRepository.removePlayer(playerId);
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
