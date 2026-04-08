package com.spotlight.ui.activity;

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
import com.spotlight.R;
import com.spotlight.logic.QuestionRepository;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.ui.adapter.AnswerChoiceAdapter;
import com.spotlight.ui.adapter.ResultAdapter;
import com.spotlight.util.AvatarUtils;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import java.io.Serializable;
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
    private View viewPassToAvatar;
    private EditText editTextAnswer;
    private Button buttonSubmitAnswer, buttonReady, buttonAction, buttonScoreSheet, buttonLogs;
    private RecyclerView recyclerViewChoices, recyclerViewResults;
    private View buttonLeave;
    private LottieAnimationView animationViewConfetti, animationViewReveal;
    private MediaPlayer mediaPlayerCorrect, mediaPlayerReveal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 1. Initialize views immediately so they are ready for data
        initViews();

        try {
            // 2. Safely retrieve player data
            if (getIntent() != null && getIntent().hasExtra("players")) {
                Serializable serializablePlayers = getIntent().getSerializableExtra("players");
                if (serializablePlayers instanceof List) {
                    this.players = new ArrayList<>((List<Player>) serializablePlayers);
                }
            }

            isMultiplayer = getIntent().getBooleanExtra("isMultiplayer", false);
            roomCode = getIntent().getStringExtra("roomCode");
            playerId = getIntent().getStringExtra("playerId");
            hostId = getIntent().getStringExtra("hostId");

            if (players == null || players.isEmpty()) {
                Toast.makeText(this, R.string.error_no_players, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 3. Keep players in original order to respect join sequence
            // No sorting here anymore

            // 4. Load repository and filter
            questionRepository = new QuestionRepository(this);
            String category = getIntent().getStringExtra("category");
            if (category != null && !category.equals("All")) {
                questionRepository.filterByCategory(category);
            }
            
            // 5. Start the game logic
            if (isMultiplayer) {
                setupMultiplayer();
            } else {
                startNewRoundLocal();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_critical, e.getMessage()), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupMultiplayer() {
        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        
        // Host initializes the first round
        if (isHost()) {
            // Sort players by join timestamp to guarantee reverse-join order (Last -> Host)
            Collections.sort(this.players, (p1, p2) -> Long.compare(p1.getJoinTimestamp(), p2.getJoinTimestamp()));
            
            currentQuestion = questionRepository.getRandomQuestion();
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("currentQuestion", currentQuestion.getText());
            
            if (!this.players.isEmpty()) {
                spotlightPlayerIndex = this.players.size() - 1; // Last player who joined
                updates.put("spotlightPlayerId", this.players.get(spotlightPlayerIndex).getId());
            }
            
            updates.put("status", "WAITING_FOR_ANSWERS");
            roomRef.updateChildren(updates);
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
        
        // Sync local players list and maintain join-timestamp order
        if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
            this.players = new ArrayList<>(room.getPlayers().values());
            Collections.sort(this.players, (p1, p2) -> Long.compare(p1.getJoinTimestamp(), p2.getJoinTimestamp()));
            
            // Re-sync the local index to match the current spotlight ID
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).getId().equals(spotlightId)) {
                    spotlightPlayerIndex = i;
                    break;
                }
            }
        }

        // Update local state
        if (questionText != null) {
            textViewQuestion.setText(questionText);
        }
        
        Player spotlightPlayer = room.getPlayers().get(spotlightId);
        
        // Host logic for phase transitions
        if (isHost()) {
            if ("WAITING_FOR_ANSWERS".equals(status)) {
                // Ensure we have guesses from EVERY player before moving to REVIEW
                if (room.getGuesses() != null && room.getGuesses().size() >= room.getPlayers().size()) {
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
            multiplayerMatchedIndices.clear(); // Reset matches for the new round
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
            updateSpotlightAvatar(spotlightPlayer);
        }
    }

    private void updateSpotlightAvatar(Player spotlightPlayer) {
        View viewSpotlightAvatar = findViewById(R.id.viewSpotlightAvatar);
        if (viewSpotlightAvatar != null) {
            AvatarUtils.setAvatarColor(viewSpotlightAvatar, spotlightPlayer.getAvatarColor());
            viewSpotlightAvatar.setVisibility(spotlightPlayer.getAvatarColor() != 0 ? View.VISIBLE : View.GONE);
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

        Set<String> matchingPlayerIds = new HashSet<>();
        for (Map.Entry<String, String> entry : guesses.entrySet()) {
            if (!entry.getKey().equals(spotlightId) && entry.getValue().equalsIgnoreCase(spotlightAnswer)) {
                matchingPlayerIds.add(entry.getKey());
            }
        }

        for (String pid : matchingPlayerIds) {
            Player p = playersMap.get(pid);
            if (p != null) {
                p.addScore(4);
                logs.add(p.getName() + " matched the Spotlight's answer! +4");
            }
        }

        // Process votes if we came from Voting phase or if no one matched
        if ("VOTING".equals(room.getStatus()) || matchingPlayerIds.isEmpty()) {
            int spotlightPoints = 0;
            Map<String, Integer> authorBonuses = new HashMap<>();

            if (votes != null) {
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
        } else {
            logs.add("Round ended immediately due to a match.");
        }

        if (logs.isEmpty()) {
            logs.add("No points were awarded this round.");
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

    private java.util.Set<Integer> multiplayerMatchedIndices = new java.util.HashSet<>();

    private Phase lastProcessedPhase = null;

    private void updateMultiplayerUI(GameRoom room, Player spotlightPlayer) {
        if (currentPhase == null) return;

        // Prevent UI flickering/freezing by only resetting major components on phase change
        boolean phaseChanged = (currentPhase != lastProcessedPhase);
        lastProcessedPhase = currentPhase;

        layoutAnswerInput.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.GONE);
        layoutPassDevice.setVisibility(View.GONE);
        layoutResults.setVisibility(View.GONE);
        buttonAction.setVisibility(View.GONE);

        boolean isSpotlight = playerId.equals(spotlightPlayer.getId());

        switch (currentPhase) {
            case WAITING_FOR_ANSWERS:
                textViewPhaseTitle.setText(isSpotlight ? getString(R.string.phase_spotlight) : getString(R.string.phase_guessing));
                textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, spotlightPlayer.getName()));
                textViewTargetPlayer.setTextColor(getResources().getColor(R.color.accent_yellow));
                textViewTargetPlayer.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                
                Map<String, String> currentGuesses = room.getGuesses();
                if (currentGuesses == null || !currentGuesses.containsKey(playerId)) {
                    layoutAnswerInput.setVisibility(View.VISIBLE);
                    editTextAnswer.setHint(isSpotlight ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
                } else {
                    textViewQuestion.setText(R.string.waiting_for_others);
                }
                break;

            case REVIEW:
                textViewPhaseTitle.setText(R.string.phase_review);
                if (isSpotlight) {
                    layoutSelection.setVisibility(View.VISIBLE);
                    textViewReviewInstructions.setVisibility(View.VISIBLE);
                    String secret = room.getGuesses().get(playerId);
                    textViewSelectionPrompt.setText(getString(R.string.your_answer_format, (secret != null ? secret : "")));
                    
                    if (phaseChanged || recyclerViewChoices.getAdapter() == null) {
                        prepareReviewUI(room);
                    }
                    
                    buttonAction.setVisibility(View.VISIBLE);
                    buttonAction.setEnabled(true);
                    buttonAction.setText(multiplayerMatchedIndices.isEmpty() ? getString(R.string.button_start_voting) : getString(R.string.button_reveal_results));
                } else {
                    textViewQuestion.setText(R.string.spotlight_reviewing);
                    textViewReviewInstructions.setVisibility(View.GONE);
                }
                break;

            case VOTING:
                textViewPhaseTitle.setText(R.string.phase_voting);
                textViewReviewInstructions.setVisibility(View.GONE);
                if (isSpotlight) {
                    textViewQuestion.setText(R.string.wait_for_votes);
                } else {
                    Map<String, String> currentVotes = room.getVotes();
                    if (currentVotes == null || !currentVotes.containsKey(playerId)) {
                        layoutSelection.setVisibility(View.VISIBLE);
                        textViewSelectionPrompt.setText(R.string.selection_prompt);
                        if (phaseChanged || recyclerViewChoices.getAdapter() == null) {
                            prepareMultiplayerVotingUI(room, spotlightPlayer);
                        }
                    } else {
                        textViewQuestion.setText(R.string.waiting_for_other_votes);
                        layoutSelection.setVisibility(View.GONE);
                    }
                }
                break;

            case RESULTS:
                textViewPhaseTitle.setText(R.string.phase_round_results);
                layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText(isHost() ? getString(R.string.button_next_round) : getString(R.string.button_wait_for_host));
                buttonAction.setEnabled(isHost());
                break;

            case FINISHED:
                textViewPhaseTitle.setText(R.string.phase_game_over);
                layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                showConfettiAnimation();
                if (isHost()) {
                    buttonAction.setVisibility(View.VISIBLE);
                    buttonAction.setEnabled(true);
                    buttonAction.setText(R.string.button_back_to_menu);
                }
                break;
        }
    }

    private void prepareReviewUI(GameRoom room) {
        List<String> options = new ArrayList<>();
        Map<String, String> guesses = room.getGuesses();
        String spotlightId = room.getSpotlightPlayerId();
        
        // Use a stable order for options to map positions correctly
        List<String> playerIds = new ArrayList<>(guesses.keySet());
        Collections.sort(playerIds);
        List<String> guessAuthors = new ArrayList<>();

        for (String pid : playerIds) {
            if (!pid.equals(spotlightId)) {
                options.add(guesses.get(pid));
                guessAuthors.add(pid);
            }
        }

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        AnswerChoiceAdapter reviewAdapter = new AnswerChoiceAdapter(options, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {}

            @Override
            public void onMatchClicked(String choice, int position) {
                String authorId = guessAuthors.get(position);
                String spotlightAnswer = guesses.get(spotlightId);
                
                if (multiplayerMatchedIndices.contains(position)) {
                    multiplayerMatchedIndices.remove(position);
                    // Optionally revert the guess if needed, but let's keep it simple for now
                } else {
                    multiplayerMatchedIndices.add(position);
                    roomRef.child("guesses").child(authorId).setValue(spotlightAnswer);
                }
                
                // Update button text immediately without waiting for Firebase sync
                buttonAction.setText(multiplayerMatchedIndices.isEmpty() ? getString(R.string.button_start_voting) : getString(R.string.button_reveal_results));
                // Notify adapter to show highlights
                ((AnswerChoiceAdapter)recyclerViewChoices.getAdapter()).setMatchedPositions(multiplayerMatchedIndices);
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
        reviewAdapter.setMatchedPositions(multiplayerMatchedIndices);
        recyclerViewChoices.setAdapter(reviewAdapter);
    }

    private void prepareMultiplayerVotingUI(GameRoom room, Player spotlightPlayer) {
        List<String> options = new ArrayList<>();
        Map<String, String> guesses = room.getGuesses();
        String spotlightId = room.getSpotlightPlayerId();
        
        // Add the spotlight's actual answer
        String secret = guesses.get(spotlightId);
        if (secret != null) options.add(secret);
        
        // Add all unique guesses from other players (excluding the spotlight)
        for (Map.Entry<String, String> entry : guesses.entrySet()) {
            String authorId = entry.getKey();
            String guess = entry.getValue();
            
            if (!authorId.equals(spotlightId)) {
                options.add(guess);
            }
        }

        // De-duplicate (case-insensitive) to hide authorship
        List<String> uniqueOptions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String opt : options) {
            if (opt != null && seen.add(opt.trim().toLowerCase())) {
                uniqueOptions.add(opt);
            }
        }
        
        // Shuffle so the correct answer isn't always first
        Collections.shuffle(uniqueOptions);

        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewChoices.setAdapter(new AnswerChoiceAdapter(uniqueOptions, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {
                // Prevent voting for your own guess if it's in the list
                String myGuess = guesses.get(playerId);
                if (myGuess != null && myGuess.equalsIgnoreCase(choice)) {
                    Toast.makeText(GameActivity.this, R.string.error_vote_own_answer, Toast.LENGTH_SHORT).show();
                    return;
                }

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
            textViewQuestion.setText(getString(R.string.secret_answer_was, secret));
        } else {
            textViewQuestion.setText(R.string.round_ended_matches);
        }

        if ("FINISHED".equals(room.getStatus())) {
            textViewTargetPlayer.setText(getString(R.string.winner_announcement, playerList.get(0).getName()));
        }
    }

    private void setupLottieView(LottieAnimationView view) {
        if (view == null) return;
        view.setRenderMode(RenderMode.HARDWARE);
        view.setFailureListener(result -> {
            view.setVisibility(View.GONE);
            android.util.Log.e("LottieError", "Failed to load animation: " + result.getMessage());
        });
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
        viewPassToAvatar = findViewById(R.id.viewPassToAvatar);
        
        View viewSpotlightAvatar = findViewById(R.id.viewSpotlightAvatar);

        editTextAnswer = findViewById(R.id.editTextAnswer);
        buttonSubmitAnswer = findViewById(R.id.buttonSubmitAnswer);
        buttonReady = findViewById(R.id.buttonReady);
        buttonAction = findViewById(R.id.buttonAction);
        
        // Handle potential missing views gracefully
        View bLeave = findViewById(R.id.buttonLeave);
        if (bLeave != null) {
            buttonLeave = bLeave;
            buttonLeave.setOnClickListener(v -> showLeaveConfirmation());
        }
        
        buttonScoreSheet = findViewById(R.id.buttonScoreSheet);
        buttonLogs = findViewById(R.id.buttonLogs);

        recyclerViewChoices = findViewById(R.id.recyclerViewChoices);
        recyclerViewResults = findViewById(R.id.recyclerViewResults);
        animationViewConfetti = findViewById(R.id.animationViewConfetti);
        animationViewReveal = findViewById(R.id.animationViewReveal);

        setupLottieView(animationViewConfetti);
        setupLottieView(animationViewReveal);

        if (buttonScoreSheet != null) buttonScoreSheet.setOnClickListener(v -> showScoreSheet());
        if (buttonLogs != null) buttonLogs.setOnClickListener(v -> showLogs());

        if (buttonSubmitAnswer != null) {
            buttonSubmitAnswer.setOnClickListener(v -> {
                if (isMultiplayer) {
                    submitMultiplayerAnswer();
                } else {
                    handleSubmitAnswer();
                }
            });
        }
        
        if (buttonReady != null) buttonReady.setOnClickListener(v -> handleReady());
        
        if (buttonAction != null) {
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
        }
    }

    private void startVotingPhase() {
        // If the Spotlight has confirmed any matches, skip voting and go straight to results.
        if (!multiplayerMatchedIndices.isEmpty()) {
            roomRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    GameRoom room = task.getResult().getValue(GameRoom.class);
                    if (room != null) {
                        calculateScoresAndMoveToResults(room);
                    }
                }
            });
        } else {
            // No matches confirmed, proceed to the voting phase as requested.
            roomRef.child("status").setValue("VOTING");
        }
    }

    private void submitMultiplayerAnswer() {
        String answer = editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;
        
        roomRef.child("guesses").child(playerId).setValue(answer);
        editTextAnswer.setText("");
        layoutAnswerInput.setVisibility(View.GONE);
        textViewQuestion.setText(R.string.waiting_for_others);
    }

    private void startNextMultiplayerRound() {
        if (!isHost()) return;

        // Reset round-specific data in an atomic update
        Map<String, Object> updates = new HashMap<>();
        updates.put("guesses", null);
        updates.put("votes", null);

        // Move to next spotlight player in reverse join order
        spotlightPlayerIndex = (spotlightPlayerIndex - 1 + players.size()) % players.size();
        String nextSpotlightId = players.get(spotlightPlayerIndex).getId();
        updates.put("spotlightPlayerId", nextSpotlightId);

        // Get a new question
        Question nextQuestion = questionRepository.getRandomQuestion();
        updates.put("currentQuestion", nextQuestion.getText());
        
        // Reset status to wait for new answers
        updates.put("status", "WAITING_FOR_ANSWERS");

        roomRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Locally clear matches so the UI updates immediately for the host
                multiplayerMatchedIndices.clear();
            }
        });
    }

    private void showScoreSheet() {
        if (players == null) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.scoreboard_desc));
        
        List<Player> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        
        for (Player p : sortedPlayers) {
            sb.append(p.getName()).append(": ").append(p.getScore()).append(" pts\n");
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_scoreboard_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.ok, null)
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
                    Toast.makeText(this, R.string.error_load_logs, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            displayLogsDialog(localLogs);
        }
    }

    private void displayLogsDialog(List<String> logs) {
        if (logs == null || logs.isEmpty()) {
            Toast.makeText(this, R.string.error_no_logs, Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append("• ").append(log).append("\n");
        }
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_logs_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.ok, null)
            .show();
    }

    private void showLeaveConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.dialog_leave_game_title)
                .setMessage(R.string.dialog_leave_game_message)
                .setPositiveButton(R.string.leave, (dialog, which) -> {
                    if (isMultiplayer) {
                        roomRef.child("players").child(playerId).removeValue();
                    }
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
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
        textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, spotlight.getName()));
        textViewTargetPlayer.setTextColor(getResources().getColor(R.color.accent_yellow));
        textViewTargetPlayer.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textViewQuestion.setText(currentQuestion.getText());
        
        updateSpotlightAvatar(spotlight);

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                textViewPhaseTitle.setText(R.string.phase_answers);
                showPassDevice(players.get(currentPlayerIndex).getName());
                break;
            case REVIEW:
                textViewPhaseTitle.setText(R.string.phase_review);
                showPassDevice(players.get(spotlightPlayerIndex).getName());
                
                // Check for matches in local mode to update button
                if (!localMatchedPlayerIndices.isEmpty()) {
                    buttonAction.setText(R.string.button_reveal_results);
                } else {
                    buttonAction.setText(R.string.button_start_voting);
                }
                break;
            case VOTING:
                textViewPhaseTitle.setText(R.string.phase_voting);
                // In local mode, we don't need pass device for the start of voting, 
                // but we need to pass to the first guesser.
                int firstGuesser = (spotlightPlayerIndex + 1) % players.size();
                showPassDevice(players.get(firstGuesser).getName());
                break;
            case RESULTS:
                textViewPhaseTitle.setText(R.string.phase_results);
                layoutResults.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText(R.string.button_next_round);
                buttonAction.setEnabled(true);
                showLocalResults();
                break;
            case FINISHED:
                textViewPhaseTitle.setText(R.string.phase_game_over_local);
                layoutResults.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText(R.string.button_exit_game);
                showLocalResults();
                showConfettiAnimation();
                break;
            default:
                textViewPhaseTitle.setText(phase.name());
                break;
        }
    }

    private void showConfettiAnimation() {
        if (animationViewConfetti == null) return;
        try {
            animationViewConfetti.enableMergePathsForKitKatAndAbove(true);
            animationViewConfetti.setVisibility(View.VISIBLE);
            animationViewConfetti.playAnimation();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (animationViewConfetti != null) {
                    animationViewConfetti.setVisibility(View.GONE);
                }
            }, 5000);
        } catch (Exception e) {
            if (animationViewConfetti != null) animationViewConfetti.setVisibility(View.GONE);
        }
    }

    private void showRevealAnimation(Runnable onComplete) {
        if (animationViewReveal == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        try {
            animationViewReveal.enableMergePathsForKitKatAndAbove(true);
            animationViewReveal.setVisibility(View.VISIBLE);
            animationViewReveal.playAnimation();
            playRevealSound();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (animationViewReveal != null) {
                    animationViewReveal.setVisibility(View.GONE);
                }
                if (onComplete != null) onComplete.run();
            }, 6000); // Increased from 3000 to 6000
        } catch (Exception e) {
            if (animationViewReveal != null) animationViewReveal.setVisibility(View.GONE);
            if (onComplete != null) onComplete.run();
        }
    }

    private void playCorrectSound() {
        try {
            if (mediaPlayerCorrect == null) {
                // Check if the resource exists before creating
                int resId = getResources().getIdentifier("correct", "raw", getPackageName());
                if (resId != 0) {
                    mediaPlayerCorrect = MediaPlayer.create(this, resId);
                }
            }
            if (mediaPlayerCorrect != null) {
                mediaPlayerCorrect.start();
            }
        } catch (Exception e) {
            // Sound failed to play
        }
    }

    private void playRevealSound() {
        try {
            if (mediaPlayerReveal == null) {
                int resId = getResources().getIdentifier("drumroll", "raw", getPackageName());
                if (resId != 0) {
                    mediaPlayerReveal = MediaPlayer.create(this, resId);
                }
            }
            if (mediaPlayerReveal != null) {
                mediaPlayerReveal.start();
            }
        } catch (Exception e) {
            // Sound failed to play
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayerCorrect != null) {
            mediaPlayerCorrect.release();
            mediaPlayerCorrect = null;
        }
        if (mediaPlayerReveal != null) {
            mediaPlayerReveal.release();
            mediaPlayerReveal = null;
        }
        if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
    }

    private void showPassDevice(String name) {
        layoutPassDevice.setVisibility(View.VISIBLE);
        textViewPassTo.setText(getString(R.string.pass_device_to, name));
        
        // Find the player to get their color
        Player targetPlayer = null;
        for (Player p : players) {
            if (p.getName().equals(name)) {
                targetPlayer = p;
                break;
            }
        }
        
        if (targetPlayer != null) {
            AvatarUtils.setAvatarColor(viewPassToAvatar, targetPlayer.getAvatarColor());
            viewPassToAvatar.setVisibility(targetPlayer.getAvatarColor() != 0 ? View.VISIBLE : View.GONE);
        } else {
            viewPassToAvatar.setVisibility(View.GONE);
        }
    }

    private void handleReady() {
        layoutPassDevice.setVisibility(View.GONE);
        if (currentPhase == Phase.WAITING_FOR_ANSWERS) {
            layoutAnswerInput.setVisibility(View.VISIBLE);
            editTextAnswer.setHint(currentPlayerIndex == spotlightPlayerIndex ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
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
        textViewSelectionPrompt.setText(R.string.review_prompt);
        textViewReviewInstructions.setVisibility(View.VISIBLE);
        buttonAction.setVisibility(View.VISIBLE);
        buttonAction.setText(R.string.button_start_voting);

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
                
                // Update button text immediately
                if (currentPhase == Phase.REVIEW) {
                    buttonAction.setText(localMatchedPlayerIndices.isEmpty() ? R.string.button_start_voting : R.string.button_reveal_results);
                }
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
        textViewSelectionPrompt.setText(getString(R.string.voting_prompt_format, players.get(currentPlayerIndex).getName()));
        
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
                    Toast.makeText(GameActivity.this, R.string.error_choose_own_answer, Toast.LENGTH_SHORT).show();
                    return;
                }

                localVotesMap.put(currentPlayerIndex, choice);
                layoutSelection.setVisibility(View.GONE);
                
                // Move to next voter (Skipping Spotlight)
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                if (currentPlayerIndex == spotlightPlayerIndex) {
                    currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                }
                
                // If we've circled back to the first voter or through everyone, finish voting
                if (localVotesMap.size() < players.size() - 1) {
                    setPhase(Phase.VOTING);
                } else {
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
                localLogs.add(getString(R.string.log_matched_review, p.getName()));
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
                    localLogs.add(getString(R.string.log_correct_vote, voter.getName()));
                } else {
                    // Check if they voted for another player's answer
                    for (int j = 0; j < localAnswers.size(); j++) {
                        if (j != spotlightPlayerIndex && !localDeletedPlayerIndices.contains(j) 
                                && localAnswers.get(j).equalsIgnoreCase(vote)) {
                            players.get(j).addScore(1);
                            localLogs.add(getString(R.string.log_voted_other, voter.getName(), players.get(j).getName(), players.get(j).getName()));
                        }
                    }
                }
            }
            
            players.get(spotlightPlayerIndex).addScore(spotlightBonus);
            if (spotlightBonus > 0) {
                localLogs.add(getString(R.string.log_spotlight_bonus, players.get(spotlightPlayerIndex).getName(), spotlightBonus));
                playCorrectSound();
            }
        }

        if (localLogs.isEmpty()) {
            localLogs.add(getString(R.string.log_no_points));
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
            textViewQuestion.setText(getString(R.string.secret_answer_was, secret));
        }

        if (currentPhase == Phase.FINISHED) {
            textViewTargetPlayer.setText(getString(R.string.winner_announcement, playerList.get(0).getName()));
        }

        buttonLogs.setVisibility(View.VISIBLE);
    }

    private void handleAction() {
        if (currentPhase == Phase.RESULTS) {
            startNewRoundLocal();
        } else if (currentPhase == Phase.FINISHED) {
            finish();
        } else if (currentPhase == Phase.REVIEW) {
            if (!localMatchedPlayerIndices.isEmpty()) {
                calculateLocalScores();
            } else {
                // Start Voting Phase after Review
                currentPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
                setPhase(Phase.VOTING);
            }
        }
    }
}
