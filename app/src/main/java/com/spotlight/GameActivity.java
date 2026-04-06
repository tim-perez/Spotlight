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
    private Button buttonSubmitAnswer, buttonReady, buttonAction;
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
                if (p != null) p.addScore(4);
            }
        } else {
            // No matches, process votes
            int spotlightPoints = 0;
            for (Map.Entry<String, String> entry : votes.entrySet()) {
                String voterId = entry.getKey();
                String votedAnswer = entry.getValue();

                if (votedAnswer.equalsIgnoreCase(spotlightAnswer)) {
                    // Rule: +2 for correct guesser
                    Player voter = playersMap.get(voterId);
                    if (voter != null) voter.addScore(2);
                    // Rule: +1 for spotlight for each correct guess
                    spotlightPoints += 1;
                } else {
                    // Rule: +1 for each player whose answer is selected instead of the spotlight's
                    for (Map.Entry<String, String> gEntry : guesses.entrySet()) {
                        String authorId = gEntry.getKey();
                        String authoredAnswer = gEntry.getValue();
                        if (!authorId.equals(spotlightId) && authoredAnswer.equalsIgnoreCase(votedAnswer)) {
                            Player author = playersMap.get(authorId);
                            if (author != null) author.addScore(1);
                        }
                    }
                }
            }
            Player spotlightPlayer = playersMap.get(spotlightId);
            if (spotlightPlayer != null) {
                spotlightPlayer.addScore(spotlightPoints);
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
            public void onMatchClicked(String choice) {
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
            public void onDeleteClicked(String choice) {
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

        recyclerViewChoices = findViewById(R.id.recyclerViewChoices);
        recyclerViewResults = findViewById(R.id.recyclerViewResults);

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
        roomRef.child("guesses").removeValue();
        roomRef.child("votes").removeValue();
        
        spotlightPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
        roomRef.child("spotlightPlayerId").setValue(players.get(spotlightPlayerIndex).getId());
        
        currentQuestion = questionRepository.getRandomQuestion();
        roomRef.child("currentQuestion").setValue(currentQuestion.getText());
        roomRef.child("status").setValue("WAITING_FOR_ANSWERS");
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

    private void startNewRoundLocal() { }
    private void setPhase(Phase phase) { }
    private void handleReady() { }
    private void handleSubmitAnswer() { }
    private void handleAction() { }
}
