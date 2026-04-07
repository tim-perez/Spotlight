package com.spotlight;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.spotlight.logic.QuestionRepository;
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

public class GameActivity extends AppCompatActivity {

    private enum Phase {
        WAITING_FOR_ANSWERS,
        REVIEW,
        VOTING,
        RESULTS,
        FINISHED
    }

    private List<Player> players;
    private String playerId;
    private String roomCode;
    private String hostId;
    private boolean isMultiplayer;
    
    private int spotlightPlayerIndex = 0;
    private Phase currentPhase;
    private QuestionRepository questionRepository;
    private Question currentQuestion;
    
    private DatabaseReference roomRef;
    private ValueEventListener roomListener;

    private TextView textViewPhaseTitle, textViewTargetPlayer, textViewQuestion, textViewPassTo, textViewSelectionPrompt, textViewReviewInstructions;
    private LinearLayout layoutAnswerInput, layoutSelection, layoutPassDevice, layoutResults;
    private EditText editTextAnswer;
    private Button buttonSubmitAnswer, buttonReady, buttonAction, buttonScoreSheet, buttonLogs;
    private RecyclerView recyclerViewChoices, recyclerViewResults;
    private View buttonLeave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        players = (ArrayList<Player>) getIntent().getSerializableExtra("players");
        isMultiplayer = getIntent().getBooleanExtra("isMultiplayer", false);
        roomCode = getIntent().getStringExtra("roomCode");
        playerId = getIntent().getStringExtra("playerId");
        hostId = getIntent().getStringExtra("hostId");

        if (players == null || (isMultiplayer && players.isEmpty())) {
            finish();
            return;
        }

        // Sort players by ID to ensure consistent order across all devices
        if (players != null) {
            Collections.sort(players, (p1, p2) -> {
                String id1 = p1.getId() != null ? p1.getId() : "";
                String id2 = p2.getId() != null ? p2.getId() : "";
                return id1.compareTo(id2);
            });
        }

        questionRepository = new QuestionRepository();
        initViews();
        
        if (isMultiplayer) {
            setupMultiplayer();
        } else {
            startNewRoundLocal();
        }
    }

    private void setupMultiplayer() {
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        
        // Host initializes the first question
        if (isHost()) {
            currentQuestion = questionRepository.getRandomQuestion();
            roomRef.child("currentQuestion").setValue(currentQuestion.getText());
            roomRef.child("spotlightPlayerId").setValue(players.get(0).getId());
            roomRef.child("status").setValue("WAITING_FOR_ANSWERS");
        }

        roomListener = roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    handleRoomUpdate(room);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isHost() {
        if (isMultiplayer) {
            return playerId != null && playerId.equals(hostId);
        }
        return true; // In local mode, the current player is effectively the host
    }

    private void handleRoomUpdate(GameRoom room) {
        String status = room.getStatus();
        String questionText = room.getCurrentQuestion();
        String spotlightId = room.getSpotlightPlayerId();
        
        // Sync local players list to keep scores and order updated
        if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
            this.players = new ArrayList<>(room.getPlayers().values());
            Collections.sort(this.players, (p1, p2) -> {
                String id1 = p1.getId() != null ? p1.getId() : "";
                String id2 = p2.getId() != null ? p2.getId() : "";
                return id1.compareTo(id2);
            });
        }

        // Update local state
        if (questionText != null) {
            textViewQuestion.setText(questionText);
        }
        
        Player spotlightPlayer = room.getPlayers().get(spotlightId);
        
        // Host logic for phase transitions
        if (isHost()) {
            if ("WAITING_FOR_ANSWERS".equals(status)) {
                if (room.getGuesses() != null && room.getGuesses().size() == room.getPlayers().size()) {
                    roomRef.child("status").setValue("REVIEW");
                }
            } else if ("VOTING".equals(status)) {
                int totalVoters = room.getPlayers().size() - 1;
                if (room.getVotes() != null && room.getVotes().size() >= totalVoters) {
                    calculateScoresAndMoveToResults(room);
                }
            }
        }
        
        if ("WAITING_FOR_ANSWERS".equals(status) || "IN_PROGRESS".equals(status)) {
            currentPhase = Phase.WAITING_FOR_ANSWERS;
        } else if ("REVIEW".equals(status)) {
            currentPhase = Phase.REVIEW;
        } else if ("VOTING".equals(status)) {
            currentPhase = Phase.VOTING;
        } else if ("RESULTS".equals(status)) {
            currentPhase = Phase.RESULTS;
        } else if ("FINISHED".equals(status)) {
            currentPhase = Phase.FINISHED;
        }

        if (spotlightPlayer != null && currentPhase != null) {
            updateMultiplayerUI(room, spotlightPlayer);
        }
    }

    private void calculateScoresAndMoveToResults(GameRoom room) {
        // Prevent double execution if another update triggers this before status changes
        if ("RESULTS".equals(room.getStatus()) || "FINISHED".equals(room.getStatus())) {
            return;
        }

        Map<String, Player> playersMap = room.getPlayers();
        Map<String, String> guesses = room.getGuesses();
        Map<String, String> votes = room.getVotes();
        String spotlightId = room.getSpotlightPlayerId();
        String spotlightAnswer = guesses.get(spotlightId);
        List<String> logs = new ArrayList<>();

        if (spotlightAnswer == null) return;

        boolean matchFound = false;
        Set<String> matchingPlayerIds = new HashSet<>();
        
        // Check for matches with spotlight
        for (Map.Entry<String, String> entry : guesses.entrySet()) {
            if (!entry.getKey().equals(spotlightId) && entry.getValue().equalsIgnoreCase(spotlightAnswer)) {
                matchFound = true;
                matchingPlayerIds.add(entry.getKey());
            }
        }

        if (matchFound) {
            // Rule: +4 for matching players, 0 for everyone else (including spotlight)
            for (String pid : matchingPlayerIds) {
                Player p = playersMap.get(pid);
                if (p != null) {
                    p.addScore(4);
                    logs.add(p.getName() + " matched the Spotlight's answer! +4");
                }
            }
            logs.add("Round ended immediately due to a match.");
        } else {
            // No matches, process votes
            int spotlightPoints = 0;
            Map<String, Integer> authorBonuses = new HashMap<>();

            for (Map.Entry<String, String> entry : votes.entrySet()) {
                String voterId = entry.getKey();
                String votedAnswer = entry.getValue();
                Player voter = playersMap.get(voterId);
                if (voter == null) continue;

                if (votedAnswer.equalsIgnoreCase(spotlightAnswer)) {
                    // Rule: +2 for correct guesser
                    voter.addScore(2);
                    // Rule: +1 for spotlight for each correct guess
                    spotlightPoints += 1;
                    logs.add(voter.getName() + " correctly voted for the Spotlight! +2");
                } else {
                    // Rule: +1 for each player whose answer is selected instead of the spotlight's
                    for (Map.Entry<String, String> gEntry : guesses.entrySet()) {
                        String authorId = gEntry.getKey();
                        String authoredAnswer = gEntry.getValue();
                        if (!authorId.equals(spotlightId) && authoredAnswer.equalsIgnoreCase(votedAnswer)) {
                            authorBonuses.put(authorId, authorBonuses.getOrDefault(authorId, 0) + 1);
                            Player author = playersMap.get(authorId);
                            if (author != null) {
                                logs.add(voter.getName() + " voted for " + author.getName() + "'s answer. " + author.getName() + " gets +1");
                            }
                        }
                    }
                }
            }

            for (Map.Entry<String, Integer> bonus : authorBonuses.entrySet()) {
                Player author = playersMap.get(bonus.getKey());
                if (author != null) author.addScore(bonus.getValue());
            }

            Player spotlightPlayer = playersMap.get(spotlightId);
            if (spotlightPlayer != null) {
                spotlightPlayer.addScore(spotlightPoints);
                if (spotlightPoints > 0) {
                    logs.add(spotlightPlayer.getName() + " received " + spotlightPoints + " point(s) from correct guesses.");
                }
            }
            
            if (logs.isEmpty()) {
                logs.add("No points were awarded this round.");
            }
        }

        // Check for game winner (25 points)
        boolean gameOver = false;
        for (Player p : playersMap.values()) {
            if (p.getScore() >= 25) {
                gameOver = true;
                break;
            }
        }

        // Atomic update to prevent race conditions and multiple triggers
        Map<String, Object> updates = new HashMap<>();
        updates.put("players", playersMap);
        updates.put("logs", logs);
        if (gameOver) {
            updates.put("status", "FINISHED");
        } else {
            updates.put("status", "RESULTS");
        }
        roomRef.updateChildren(updates);
    }

    private void updateMultiplayerUI(GameRoom room, Player spotlightPlayer) {
        if (currentPhase == null) return;

        layoutAnswerInput.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.GONE);
        layoutPassDevice.setVisibility(View.GONE);
        layoutResults.setVisibility(View.GONE);
        buttonAction.setVisibility(View.GONE);

        boolean isSpotlight = playerId.equals(spotlightPlayer.getId());

        switch (currentPhase) {
            case WAITING_FOR_ANSWERS:
                textViewPhaseTitle.setText(isSpotlight ? "You are the Spotlight!" : "Guessing Phase");
                textViewTargetPlayer.setText(spotlightPlayer.getName() + " is in the Spotlight");
                
                if (!room.getGuesses().containsKey(playerId)) {
                    layoutAnswerInput.setVisibility(View.VISIBLE);
                    editTextAnswer.setHint(isSpotlight ? "Your secret answer" : "Guess their answer");
                } else {
                    textViewQuestion.setText("Waiting for others...");
                }
                break;

            case REVIEW:
                textViewPhaseTitle.setText("Review Phase");
                if (isSpotlight) {
                    layoutSelection.setVisibility(View.VISIBLE);
                    textViewReviewInstructions.setVisibility(View.VISIBLE);
                    String secret = room.getGuesses().get(playerId);
                    textViewSelectionPrompt.setText("Your Answer: " + (secret != null ? secret : ""));
                    prepareReviewUI(room);
                    buttonAction.setVisibility(View.VISIBLE);
                    buttonAction.setText("Start Voting");
                } else {
                    textViewQuestion.setText("Spotlight is reviewing answers...");
                    textViewReviewInstructions.setVisibility(View.GONE);
                }
                break;

            case VOTING:
                textViewPhaseTitle.setText("Voting Phase");
                textViewReviewInstructions.setVisibility(View.GONE);
                if (isSpotlight) {
                    textViewQuestion.setText("Wait for others to vote!");
                } else {
                    if (!room.getVotes().containsKey(playerId)) {
                        layoutSelection.setVisibility(View.VISIBLE);
                        textViewSelectionPrompt.setText("Pick the Spotlight player's answer:");
                        prepareMultiplayerVotingUI(room, spotlightPlayer);
                    } else {
                        textViewQuestion.setText("Waiting for other votes...");
                    }
                }
                break;

            case RESULTS:
                textViewPhaseTitle.setText("Round Results");
                layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                if (isHost()) {
                    buttonAction.setVisibility(View.VISIBLE);
                    buttonAction.setText("Next Round");
                }
                break;

            case FINISHED:
                textViewPhaseTitle.setText("GAME OVER!");
                layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                if (isHost()) {
                    buttonAction.setVisibility(View.VISIBLE);
                    buttonAction.setText("Back to Menu");
                }
                break;
        }
    }

    private void prepareReviewUI(GameRoom room) {
        List<String> options = new ArrayList<>();
        Map<String, String> guesses = room.getGuesses();
        String spotlightId = room.getSpotlightPlayerId();
        
        for (Map.Entry<String, String> entry : guesses.entrySet()) {
            if (!entry.getKey().equals(spotlightId)) {
                options.add(entry.getValue());
            }
        }

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        AnswerChoiceAdapter reviewAdapter = new AnswerChoiceAdapter(options, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {}

            @Override
            public void onMatchClicked(String choice, int position) {
                // Match: Set this player's guess to EXACTLY the spotlight's answer (case sync)
                String spotlightAnswer = guesses.get(spotlightId);
                for (Map.Entry<String, String> entry : guesses.entrySet()) {
                    if (!entry.getKey().equals(spotlightId) && entry.getValue().equalsIgnoreCase(choice)) {
                        roomRef.child("guesses").child(entry.getKey()).setValue(spotlightAnswer);
                        break;
                    }
                }
            }

            @Override
            public void onDeleteClicked(int position) {
                String choice = options.get(position);
                // Duplicate: Remove the guess
                for (Map.Entry<String, String> entry : guesses.entrySet()) {
                    if (!entry.getKey().equals(spotlightId) && entry.getValue().equalsIgnoreCase(choice)) {
                        roomRef.child("guesses").child(entry.getKey()).removeValue();
                        break;
                    }
                }
            }
        });
        reviewAdapter.setReviewMode(true);
        recyclerViewChoices.setAdapter(reviewAdapter);
    }

    private void prepareMultiplayerVotingUI(GameRoom room, Player spotlightPlayer) {
        List<String> options = new ArrayList<>();
        String secret = room.getGuesses().get(spotlightPlayer.getId());
        if (secret != null) options.add(secret);
        
        Map<String, String> guesses = room.getGuesses();
        for (Map.Entry<String, String> entry : guesses.entrySet()) {
            String authorId = entry.getKey();
            String guess = entry.getValue();
            // Don't show the spotlight's answer (it's added separately as 'secret')
            // and don't show the player's own answer to them
            if (!authorId.equals(spotlightPlayer.getId()) && !authorId.equals(playerId)) {
                options.add(guess);
            }
        }
        // Remove duplicates from voting list (case insensitive)
        List<String> uniqueOptions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String opt : options) {
            if (seen.add(opt.toLowerCase())) {
                uniqueOptions.add(opt);
            }
        }
        Collections.shuffle(uniqueOptions);

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChoices.setAdapter(new AnswerChoiceAdapter(uniqueOptions, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {
                roomRef.child("votes").child(playerId).setValue(choice);
                layoutSelection.setVisibility(View.GONE);
                textViewQuestion.setText("Waiting for other votes...");
            }
        }));
    }

    private void showMultiplayerResultsUI(GameRoom room) {
        List<Player> playerList = new ArrayList<>(room.getPlayers().values());
        Collections.sort(playerList, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewResults.setAdapter(new ResultAdapter(playerList));
        
        String secret = room.getGuesses().get(room.getSpotlightPlayerId());
        if (secret != null) {
            textViewQuestion.setText("Secret Answer was: " + secret);
        } else {
            textViewQuestion.setText("Round ended (Matches/Duplicates found)");
        }

        if ("FINISHED".equals(room.getStatus())) {
            textViewTargetPlayer.setText("Winner: " + playerList.get(0).getName() + "!");
        }
    }

    private void initViews() {
        textViewPhaseTitle = findViewById(R.id.textViewPhaseTitle);
        textViewTargetPlayer = findViewById(R.id.textViewTargetPlayer);
        textViewQuestion = findViewById(R.id.textViewQuestion);
        textViewPassTo = findViewById(R.id.textViewPassTo);
        textViewSelectionPrompt = findViewById(R.id.textViewSelectionPrompt);
        textViewReviewInstructions = findViewById(R.id.textViewReviewInstructions);

        layoutAnswerInput = findViewById(R.id.layoutAnswerInput);
        layoutSelection = findViewById(R.id.layoutSelection);
        layoutPassDevice = findViewById(R.id.layoutPassDevice);
        layoutResults = findViewById(R.id.layoutResults);

        editTextAnswer = findViewById(R.id.editTextAnswer);
        buttonSubmitAnswer = findViewById(R.id.buttonSubmitAnswer);
        buttonReady = findViewById(R.id.buttonReady);
        buttonAction = findViewById(R.id.buttonAction);
        buttonLeave = findViewById(R.id.buttonLeave);
        buttonScoreSheet = findViewById(R.id.buttonScoreSheet);
        buttonLogs = findViewById(R.id.buttonLogs);

        recyclerViewChoices = findViewById(R.id.recyclerViewChoices);
        recyclerViewResults = findViewById(R.id.recyclerViewResults);

        buttonScoreSheet.setOnClickListener(v -> showScoreSheet());
        buttonLogs.setOnClickListener(v -> showLogs());

        buttonSubmitAnswer.setOnClickListener(v -> {
            if (isMultiplayer) {
                submitMultiplayerAnswer();
            } else {
                handleSubmitAnswer();
            }
        });
        buttonReady.setOnClickListener(v -> handleReady());
        buttonAction.setOnClickListener(v -> {
            if (isMultiplayer) {
                if (currentPhase == Phase.REVIEW) {
                    startVotingPhase();
                } else if (currentPhase == Phase.FINISHED && isHost()) {
                    finish();
                } else if (isHost()) {
                    startNextMultiplayerRound();
                }
            } else {
                handleAction();
            }
        });
        buttonLeave.setOnClickListener(v -> showLeaveConfirmation());
    }

    private void startVotingPhase() {
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                GameRoom room = snapshot.getValue(GameRoom.class);
                if (room != null) {
                    Map<String, String> guesses = room.getGuesses();
                    String spotlightId = room.getSpotlightPlayerId();
                    String spotlightAnswer = guesses.get(spotlightId);

                    boolean matchFound = false;
                    for (Map.Entry<String, String> entry : guesses.entrySet()) {
                        if (!entry.getKey().equals(spotlightId) && entry.getValue().equalsIgnoreCase(spotlightAnswer)) {
                            matchFound = true;
                            break;
                        }
                    }

                    if (matchFound) {
                        calculateScoresAndMoveToResults(room);
                    } else if (guesses.size() <= 1) {
                        roomRef.child("status").setValue("RESULTS");
                    } else {
                        roomRef.child("status").setValue("VOTING");
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void submitMultiplayerAnswer() {
        String answer = editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;
        
        roomRef.child("guesses").child(playerId).setValue(answer);
        editTextAnswer.setText("");
        layoutAnswerInput.setVisibility(View.GONE);
        textViewQuestion.setText("Waiting for others...");
    }

    private void startNextMultiplayerRound() {
        if (!isHost()) return;

        // Reset round-specific data
        roomRef.child("guesses").removeValue();
        roomRef.child("votes").removeValue();
        roomRef.child("logs").removeValue();

        // Move to next spotlight player
        spotlightPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
        String nextSpotlightId = players.get(spotlightPlayerIndex).getId();

        // Get a new question based on the selected category
        String category = getIntent().getStringExtra("category");
        if (category != null) {
            questionRepository.filterByCategory(category);
        }
        Question nextQuestion = questionRepository.getRandomQuestion();

        Map<String, Object> updates = new HashMap<>();
        updates.put("spotlightPlayerId", nextSpotlightId);
        updates.put("currentQuestion", nextQuestion.getText());
        updates.put("status", "WAITING_FOR_ANSWERS");

        roomRef.updateChildren(updates);
    }

    private void showScoreSheet() {
        if (players == null) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("First to 25 points wins!\n\n");
        
        List<Player> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        
        for (Player p : sortedPlayers) {
            sb.append(p.getName()).append(": ").append(p.getScore()).append(" pts\n");
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("Scoreboard")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void showLogs() {
        if (isMultiplayer) {
            roomRef.child("logs").get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<String> logs = new ArrayList<>();
                    for (DataSnapshot ds : task.getResult().getChildren()) {
                        logs.add(ds.getValue(String.class));
                    }
                    
                    displayLogsDialog(logs);
                } else {
                    Toast.makeText(this, "Failed to load logs.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            displayLogsDialog(localLogs);
        }
    }

    private void displayLogsDialog(List<String> logs) {
        if (logs == null || logs.isEmpty()) {
            Toast.makeText(this, "No logs for this round yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append("• ").append(log).append("\n");
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("Round Logs")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    private void showLeaveConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Leave Game")
                .setMessage("Are you sure you want to leave?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    if (isMultiplayer) {
                        roomRef.child("players").child(playerId).removeValue();
                    }
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int currentPlayerIndex = 0;
    private List<String> localAnswers = new ArrayList<>();
    private Set<Integer> localMatchedPlayerIndices = new HashSet<>();
    private Set<Integer> localDeletedPlayerIndices = new HashSet<>();
    private Map<Integer, String> localVotesMap = new HashMap<>();
    private List<String> localLogs = new ArrayList<>();

    private void startNewRoundLocal() {
        // Increment spotlightPlayerIndex for subsequent rounds
        if (currentPhase != null) {
            spotlightPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
        }
        currentPlayerIndex = spotlightPlayerIndex; // Spotlight starts
        localAnswers.clear();
        for (int i = 0; i < players.size(); i++) localAnswers.add(null);
        localMatchedPlayerIndices.clear();
        localDeletedPlayerIndices.clear();
        localVotesMap.clear();
        localLogs.clear();
        
        buttonLogs.setVisibility(View.GONE);

        String category = getIntent().getStringExtra("category");
        if (category != null) {
            questionRepository.filterByCategory(category);
        }
        currentQuestion = questionRepository.getRandomQuestion();
        
        setPhase(Phase.WAITING_FOR_ANSWERS);
    }

    private void setPhase(Phase phase) {
        currentPhase = phase;
        
        // Hide all layouts
        layoutPassDevice.setVisibility(View.GONE);
        layoutAnswerInput.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.GONE);
        layoutResults.setVisibility(View.GONE);
        buttonAction.setVisibility(View.GONE);
        textViewReviewInstructions.setVisibility(View.GONE);

        Player spotlight = players.get(spotlightPlayerIndex);
        textViewTargetPlayer.setText("Spotlight: " + spotlight.getName());
        textViewQuestion.setText(currentQuestion.getText());

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                textViewPhaseTitle.setText("Answers");
                showPassDevice(players.get(currentPlayerIndex).getName());
                break;
            case REVIEW:
                textViewPhaseTitle.setText("Review");
                showPassDevice(players.get(spotlightPlayerIndex).getName());
                break;
            case VOTING:
                textViewPhaseTitle.setText("Voting");
                showPassDevice(players.get(currentPlayerIndex).getName());
                break;
            case RESULTS:
                textViewPhaseTitle.setText("Results");
                layoutResults.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText("Next Round");
                showLocalResults();
                break;
            case FINISHED:
                textViewPhaseTitle.setText("Game Over");
                layoutResults.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText("Exit Game");
                showLocalResults();
                break;
        }
    }

    private void showPassDevice(String name) {
        layoutPassDevice.setVisibility(View.VISIBLE);
        textViewPassTo.setText("Pass the device to " + name);
    }

    private void handleReady() {
        layoutPassDevice.setVisibility(View.GONE);
        if (currentPhase == Phase.WAITING_FOR_ANSWERS) {
            layoutAnswerInput.setVisibility(View.VISIBLE);
            editTextAnswer.setHint(currentPlayerIndex == spotlightPlayerIndex ? "Your secret answer" : "Guess their answer");
        } else if (currentPhase == Phase.REVIEW) {
            prepareLocalReviewUI();
        } else if (currentPhase == Phase.VOTING) {
            prepareLocalVotingUI();
        }
    }

    private void handleSubmitAnswer() {
        String answer = editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;

        localAnswers.set(currentPlayerIndex, answer);
        editTextAnswer.setText("");
        
        // Circular progression
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        if (currentPlayerIndex != spotlightPlayerIndex) {
            setPhase(Phase.WAITING_FOR_ANSWERS);
        } else {
            // Circle complete, back to Spotlight for Review
            setPhase(Phase.REVIEW);
        }
    }

    private List<Integer> currentReviewOptionIndices = new ArrayList<>();

    private void prepareLocalReviewUI() {
        layoutSelection.setVisibility(View.VISIBLE);
        textViewSelectionPrompt.setText("Review and mark matches or delete duplicates:");
        textViewReviewInstructions.setVisibility(View.VISIBLE);
        buttonAction.setVisibility(View.VISIBLE);
        buttonAction.setText("Start Voting");

        updateReviewList();
    }

    private void updateReviewList() {
        currentReviewOptionIndices.clear();
        List<String> options = new ArrayList<>();
        Set<Integer> matchedUIPositions = new HashSet<>();

        for (int i = 0; i < localAnswers.size(); i++) {
            if (i != spotlightPlayerIndex && !localDeletedPlayerIndices.contains(i)) {
                currentReviewOptionIndices.add(i);
                options.add(localAnswers.get(i));
                if (localMatchedPlayerIndices.contains(i)) {
                    matchedUIPositions.add(options.size() - 1);
                }
            }
        }

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(options, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {}

            @Override
            public void onMatchClicked(String choice, int position) {
                int playerIdx = currentReviewOptionIndices.get(position);
                if (localMatchedPlayerIndices.contains(playerIdx)) {
                    localMatchedPlayerIndices.remove(playerIdx);
                } else {
                    localMatchedPlayerIndices.add(playerIdx);
                }
                updateReviewList();
            }

            @Override
            public void onDeleteClicked(int position) {
                int playerIdx = currentReviewOptionIndices.get(position);
                localDeletedPlayerIndices.add(playerIdx);
                localMatchedPlayerIndices.remove(playerIdx);
                updateReviewList();
            }
        });
        adapter.setReviewMode(true);
        adapter.setMatchedPositions(matchedUIPositions);
        recyclerViewChoices.setAdapter(adapter);
    }

    private void prepareLocalVotingUI() {
        layoutSelection.setVisibility(View.VISIBLE);
        textViewSelectionPrompt.setText(players.get(currentPlayerIndex).getName() + ", pick the Spotlight's answer:");
        
        List<String> options = new ArrayList<>();
        List<Integer> optionOriginalIndices = new ArrayList<>();

        // Add spotlight's answer
        options.add(localAnswers.get(spotlightPlayerIndex));
        optionOriginalIndices.add(spotlightPlayerIndex);

        // Add other players' answers (not deleted)
        for (int i = 0; i < localAnswers.size(); i++) {
            if (i != spotlightPlayerIndex && !localDeletedPlayerIndices.contains(i)) {
                options.add(localAnswers.get(i));
                optionOriginalIndices.add(i);
            }
        }
        
        // Final options list (unique by content, but we need to track if they pick their own)
        // Actually the rule is "Players cannot vote for their own answer".
        // Let's keep it simple: show unique answers.
        
        List<String> uniqueOptions = new ArrayList<>();
        Map<String, Set<Integer>> contentToPlayerIndices = new HashMap<>();

        for (int i = 0; i < options.size(); i++) {
            String content = options.get(i).toLowerCase();
            if (!contentToPlayerIndices.containsKey(content)) {
                uniqueOptions.add(options.get(i));
                contentToPlayerIndices.put(content, new HashSet<>());
            }
            contentToPlayerIndices.get(content).add(optionOriginalIndices.get(i));
        }

        Collections.shuffle(uniqueOptions);

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChoices.setAdapter(new AnswerChoiceAdapter(uniqueOptions, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {
                Set<Integer> authors = contentToPlayerIndices.get(choice.toLowerCase());
                if (authors != null && authors.contains(currentPlayerIndex)) {
                    Toast.makeText(GameActivity.this, "You cannot choose your own answer", Toast.LENGTH_SHORT).show();
                    return;
                }

                localVotesMap.put(currentPlayerIndex, choice);
                layoutSelection.setVisibility(View.GONE);
                
                // Move to next voter (Circular)
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                
                if (currentPlayerIndex != spotlightPlayerIndex) {
                    setPhase(Phase.VOTING);
                } else {
                    // Back to Spotlight for Results
                    calculateLocalScores();
                }
            }
        }));
    }

    private void calculateLocalScores() {
        localLogs.clear();
        String spotlightAnswer = localAnswers.get(spotlightPlayerIndex);
        int spotlightBonus = 0;
        
        // First check for matches from Review phase
        if (!localMatchedPlayerIndices.isEmpty()) {
            for (int playerIdx : localMatchedPlayerIndices) {
                Player p = players.get(playerIdx);
                p.addScore(4);
                localLogs.add(p.getName() + " matched the Spotlight's answer in Review! +4");
            }
            // If matches were found, we could skip voting? The prompt says "Spotlight -> Circle -> Review -> Circle -> Reveal"
            // So voting happens regardless of matches? Or matches end the round?
            // "Round ended immediately due to a match." is in multiplayer code.
            // Let's follow that logic for consistency if matches exist.
        } else {
            // Process votes
            for (Map.Entry<Integer, String> entry : localVotesMap.entrySet()) {
                int voterIdx = entry.getKey();
                String vote = entry.getValue();
                Player voter = players.get(voterIdx);

                if (vote.equalsIgnoreCase(spotlightAnswer)) {
                    voter.addScore(2);
                    spotlightBonus++;
                    localLogs.add(voter.getName() + " correctly voted for the Spotlight! +2");
                } else {
                    // Check if they voted for another player's answer
                    for (int j = 0; j < localAnswers.size(); j++) {
                        if (j != spotlightPlayerIndex && !localDeletedPlayerIndices.contains(j) 
                                && localAnswers.get(j).equalsIgnoreCase(vote)) {
                            players.get(j).addScore(1);
                            localLogs.add(voter.getName() + " voted for " + players.get(j).getName() + "'s answer. " + players.get(j).getName() + " gets +1");
                        }
                    }
                }
            }
            
            players.get(spotlightPlayerIndex).addScore(spotlightBonus);
            if (spotlightBonus > 0) {
                localLogs.add(players.get(spotlightPlayerIndex).getName() + " received " + spotlightBonus + " point(s) from correct guesses.");
            }
        }

        if (localLogs.isEmpty()) {
            localLogs.add("No points were awarded this round.");
        }

        boolean gameOver = false;
        for (Player p : players) {
            if (p.getScore() >= 25) {
                gameOver = true;
                break;
            }
        }
        
        setPhase(gameOver ? Phase.FINISHED : Phase.RESULTS);
    }

    private void showLocalResults() {
        List<Player> playerList = new ArrayList<>(players);
        Collections.sort(playerList, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewResults.setAdapter(new ResultAdapter(playerList));
        
        String secret = localAnswers.get(spotlightPlayerIndex);
        if (secret != null) {
            textViewQuestion.setText("Secret Answer was: " + secret);
        }

        if (currentPhase == Phase.FINISHED) {
            textViewTargetPlayer.setText("Winner: " + playerList.get(0).getName() + "!");
        }

        buttonLogs.setVisibility(View.VISIBLE);
    }

    private void handleAction() {
        if (currentPhase == Phase.RESULTS) {
            startNewRoundLocal();
        } else if (currentPhase == Phase.FINISHED) {
            finish();
        } else if (currentPhase == Phase.REVIEW) {
            // Start Voting Phase after Review
            currentPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
            setPhase(Phase.VOTING);
        }
    }
}
