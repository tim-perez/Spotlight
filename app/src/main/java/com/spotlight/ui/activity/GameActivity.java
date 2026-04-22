package com.spotlight.ui.activity;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.spotlight.R;
import com.spotlight.databinding.ActivityGameBinding;
import com.spotlight.logic.GameViewModel;
import com.spotlight.logic.GameViewModel.Phase;
import com.spotlight.logic.ViewModelFactory;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.model.RoomStatus;
import com.spotlight.ui.adapter.AnswerChoiceAdapter;
import com.spotlight.ui.adapter.ResultAdapter;
import com.spotlight.util.AvatarUtils;
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

    private ActivityGameBinding binding;
    private GameViewModel viewModel;
    
    private List<Player> players;
    private boolean isMultiplayer;
    private String roomCode;
    private String playerId;
    private String hostId;
    
    private Set<String> multiplayerMatchedAnswers = new HashSet<>();
    private String selectedVote = null;
    private Phase lastProcessedPhase = null;
    private GameRoom currentRoom = null;

    private MediaPlayer mediaPlayerCorrect, mediaPlayerReveal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewModelFactory factory = new ViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(GameViewModel.class);

        initFromIntent();
        initViews();

        if (players == null || players.isEmpty()) {
            Toast.makeText(this, R.string.error_no_players, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String category = getIntent().getStringExtra("category");
        viewModel.init(isMultiplayer, roomCode, playerId, hostId, players, category);
        setupObservers();
    }

    private void initFromIntent() {
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
    }

    private void setupObservers() {
        viewModel.getCurrentPhase().observe(this, phase -> {
            if (phase != null) {
                if (!isMultiplayer) {
                    updateUI(phase);
                } else if (currentRoom != null) {
                    handleRoomUpdate(currentRoom);
                }
            }
        });

        viewModel.getCurrentQuestion().observe(this, question -> {
            if (question != null) {
                binding.textViewQuestion.setText(question.getText());
            }
        });

        viewModel.getSpotlightPlayerIndex().observe(this, index -> {
            if (!isMultiplayer && index != null && players != null) {
                updateSpotlightUI();
            }
        });

        viewModel.getCurrentPlayerIndexLiveData().observe(this, index -> {
            if (!isMultiplayer && index != null && players != null) {
                updateUI(viewModel.getCurrentPhase().getValue());
            }
        });

        viewModel.getPlayers().observe(this, playerList -> {
            if (playerList != null) {
                this.players = playerList;
            }
        });

        if (isMultiplayer) {
            viewModel.getRoomData().observe(this, room -> {
                if (room != null) {
                    currentRoom = room;
                    handleRoomUpdate(room);
                }
            });
        }
    }

    private void initViews() {
        setupLottieView(binding.animationViewConfetti);
        setupLottieView(binding.animationViewReveal);

        binding.buttonLeave.setOnClickListener(v -> showLeaveConfirmation());
        binding.buttonScoreSheet.setOnClickListener(v -> showScoreSheet());
        binding.buttonLogs.setOnClickListener(v -> showLogs());

        binding.buttonSubmitAnswer.setOnClickListener(v -> {
            if (isMultiplayer) {
                submitMultiplayerAnswer();
            } else {
                handleSubmitAnswer();
            }
        });

        binding.buttonReady.setOnClickListener(v -> handleReady());

        binding.buttonAction.setOnClickListener(v -> {
            if (isMultiplayer) {
                handleMultiplayerAction();
            } else {
                handleLocalAction();
            }
        });
    }

    private void updateSpotlightUI() {
        Player spotlight = players.get(viewModel.getSpotlightPlayerIndex().getValue());
        binding.textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, spotlight.getName()));
        updateSpotlightAvatar(spotlight);
    }

    private void updateUI(Phase phase) {
        if (phase == null) return;
        
        binding.layoutAnswerInput.setVisibility(View.GONE);
        binding.layoutSelection.setVisibility(View.GONE);
        binding.layoutPassDevice.setVisibility(View.GONE);
        binding.layoutResults.setVisibility(View.GONE);
        binding.buttonAction.setVisibility(View.GONE);
        binding.textViewReviewInstructions.setVisibility(View.GONE);

        if (isMultiplayer) return;

        updateSpotlightUI();

        Integer currentPlayerIdx = viewModel.getCurrentPlayerIndexLiveData().getValue();
        if (currentPlayerIdx == null) currentPlayerIdx = 0;

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                binding.textViewPhaseTitle.setText(R.string.phase_answers);
                showPassDevice(players.get(currentPlayerIdx).getName());
                break;
            case REVIEW:
                binding.textViewPhaseTitle.setText(R.string.phase_review);
                showPassDevice(players.get(viewModel.getSpotlightPlayerIndex().getValue()).getName());
                break;
            case VOTING:
                binding.textViewPhaseTitle.setText(R.string.phase_voting);
                showPassDevice(players.get(currentPlayerIdx).getName());
                break;
            case RESULTS:
                binding.textViewPhaseTitle.setText(R.string.phase_results);
                binding.layoutResults.setVisibility(View.VISIBLE);
                binding.buttonAction.setVisibility(View.VISIBLE);
                binding.buttonAction.setText(R.string.button_next_round);
                binding.buttonAction.setEnabled(true);
                showLocalResults();
                break;
            case FINISHED:
                binding.textViewPhaseTitle.setText(R.string.phase_game_over_local);
                binding.layoutResults.setVisibility(View.VISIBLE);
                binding.buttonAction.setVisibility(View.VISIBLE);
                binding.buttonAction.setText(R.string.button_exit_game);
                showLocalResults();
                showWinner(players);
                showConfettiAnimation();
                break;
        }
    }

    private void handleRoomUpdate(GameRoom room) {
        if (room == null || room.getStatus() == null) return;

        Phase phase = viewModel.getCurrentPhase().getValue();
        if (phase == null) return;

// 1. HOST LOGIC: Automatically advance phases when all players have submitted
        if (viewModel.isHost()) {
            if (phase == Phase.WAITING_FOR_ANSWERS) {
                Map<String, String> guesses = room.getGuesses();
                if (guesses != null && guesses.size() == room.getPlayers().size() && room.getPlayers().size() > 0) {
                    // CRITICAL: Ensure this is the Enum, NOT the string "REVIEW"
                    viewModel.updateMultiplayerStatus(RoomStatus.REVIEW);
                }
            } else if (phase == Phase.VOTING) {
                Map<String, String> votes = room.getVotes();
                Map<String, Player> playersMap = room.getPlayers();
                if (votes != null && votes.size() == playersMap.size() - 1 && playersMap.size() > 1) {
                    viewModel.calculateMultiplayerScores(null);
                }
            }
        }

        // 2. UI LOGIC: Update multiplayer-specific views (Secret answers, voting lists)
        String spotlightId = room.getSpotlightPlayerId();
        if (spotlightId != null && room.getPlayers() != null) {
            Player spotlightPlayer = room.getPlayers().get(spotlightId);
            if (spotlightPlayer != null) {
                updateMultiplayerUI(room, spotlightPlayer);
                updateSpotlightAvatar(spotlightPlayer);
            }
        }
    }

    private void updateMultiplayerUI(GameRoom room, Player spotlightPlayer) {
        Phase phase = viewModel.getCurrentPhase().getValue();
        if (phase == null) return;

        if (lastProcessedPhase != phase) {
            binding.layoutAnswerInput.setVisibility(View.GONE);
            binding.layoutSelection.setVisibility(View.GONE);
            binding.layoutPassDevice.setVisibility(View.GONE);
            binding.layoutResults.setVisibility(View.GONE);
            binding.buttonAction.setVisibility(View.GONE);

            binding.textViewReviewInstructions.setVisibility(View.GONE);

            lastProcessedPhase = phase;
        }

        boolean isSpotlight = playerId.equals(spotlightPlayer.getId());

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                binding.textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, spotlightPlayer.getName()));

                Map<String, String> currentGuesses = room.getGuesses();
                if (currentGuesses == null || !currentGuesses.containsKey(playerId)) {
                    binding.textViewPhaseTitle.setText(isSpotlight ? getString(R.string.phase_spotlight) : getString(R.string.phase_guessing));
                    binding.layoutAnswerInput.setVisibility(View.VISIBLE);
                    binding.editTextAnswer.setHint(isSpotlight ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
                } else {
                    // THE FIX: Update the Title instead of erasing the Question!
                    binding.textViewPhaseTitle.setText(R.string.waiting_for_others);
                }

                // Ensure the question remains visible on the card
                if (viewModel.getCurrentQuestion().getValue() != null) {
                    binding.textViewQuestion.setText(viewModel.getCurrentQuestion().getValue().getText());
                }
                break;

            case REVIEW:
                if (isSpotlight) {
                    binding.textViewPhaseTitle.setText(R.string.phase_review);
                    binding.layoutSelection.setVisibility(View.VISIBLE);
                    binding.textViewReviewInstructions.setVisibility(View.VISIBLE);
                    String secret = room.getGuesses().get(playerId);
                    binding.textViewSelectionPrompt.setText(getString(R.string.your_answer_format, (secret != null ? secret : "")));
                    prepareReviewUI(room);
                    binding.buttonAction.setVisibility(View.VISIBLE);
                    binding.buttonAction.setEnabled(true);
                    binding.buttonAction.setText(multiplayerMatchedAnswers.isEmpty() ? getString(R.string.button_start_voting) : getString(R.string.button_reveal_results));
                } else {
                    binding.textViewPhaseTitle.setText(R.string.spotlight_reviewing);
                }

                if (viewModel.getCurrentQuestion().getValue() != null) {
                    binding.textViewQuestion.setText(viewModel.getCurrentQuestion().getValue().getText());
                }
                break;

            case VOTING:
                if (isSpotlight) {
                    binding.textViewPhaseTitle.setText(R.string.wait_for_votes);
                } else {
                    Map<String, String> currentVotes = room.getVotes();
                    if (currentVotes == null || !currentVotes.containsKey(playerId)) {
                        binding.textViewPhaseTitle.setText(R.string.phase_voting);
                        binding.layoutSelection.setVisibility(View.VISIBLE);
                        binding.textViewSelectionPrompt.setText(R.string.selection_prompt);
                        prepareMultiplayerVotingUI(room);
                    } else {
                        // THE FIX: Update the Title instead of erasing the Question!
                        binding.textViewPhaseTitle.setText(R.string.waiting_for_others);
                    }
                }

                if (viewModel.getCurrentQuestion().getValue() != null) {
                    binding.textViewQuestion.setText(viewModel.getCurrentQuestion().getValue().getText());
                }
                break;

            case RESULTS:
                binding.textViewPhaseTitle.setText(R.string.phase_round_results);
                binding.layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                binding.buttonAction.setVisibility(View.VISIBLE);
                binding.buttonAction.setText(viewModel.isHost() ? getString(R.string.button_next_round) : getString(R.string.button_wait_for_host));
                binding.buttonAction.setEnabled(viewModel.isHost());
                break;

            case FINISHED:
                binding.textViewPhaseTitle.setText(R.string.phase_game_over);
                binding.layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                binding.buttonAction.setVisibility(View.VISIBLE);
                binding.buttonAction.setText(R.string.button_exit_game);
                binding.buttonAction.setEnabled(true);
                showWinner(new ArrayList<>(room.getPlayers().values()));
                showConfettiAnimation();
                break;
        }
    }

    private void updateSpotlightAvatar(Player spotlightPlayer) {
        AvatarUtils.setAvatarColor(binding.viewSpotlightAvatar, spotlightPlayer.getAvatarColor());
        binding.viewSpotlightAvatar.setVisibility(spotlightPlayer.getAvatarColor() != 0 ? View.VISIBLE : View.GONE);
    }

    private void showPassDevice(String name) {
        binding.layoutPassDevice.setVisibility(View.VISIBLE);
        binding.textViewPassTo.setText(getString(R.string.pass_device_to, name));
        
        Player targetPlayer = null;
        for (Player p : players) {
            if (p.getName().equals(name)) {
                targetPlayer = p;
                break;
            }
        }
        
        if (targetPlayer != null) {
            AvatarUtils.setAvatarColor(binding.viewPassToAvatar, targetPlayer.getAvatarColor());
            binding.viewPassToAvatar.setVisibility(targetPlayer.getAvatarColor() != 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void handleReady() {
        binding.layoutPassDevice.setVisibility(View.GONE);
        Phase phase = viewModel.getCurrentPhase().getValue();
        if (phase == Phase.WAITING_FOR_ANSWERS) {
            binding.layoutAnswerInput.setVisibility(View.VISIBLE);
            binding.editTextAnswer.setHint(viewModel.getCurrentPlayerIndex() == viewModel.getSpotlightPlayerIndex().getValue() ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
        } else if (phase == Phase.REVIEW) {
            prepareLocalReviewUI();
        } else if (phase == Phase.VOTING) {
            prepareLocalVotingUI();
        }
    }

    private void handleSubmitAnswer() {
        String answer = binding.editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;
        viewModel.submitLocalAnswer(answer);
        binding.editTextAnswer.setText("");
    }

    private void handleMultiplayerAction() {
        Phase currentPhase = viewModel.getCurrentPhase().getValue();
        if (currentPhase == Phase.REVIEW) {
            startVotingPhase();
        } else if (currentPhase == Phase.VOTING) {
            if (selectedVote != null) {
                viewModel.submitMultiplayerVote(selectedVote);
                binding.layoutSelection.setVisibility(View.GONE);
                binding.buttonAction.setVisibility(View.GONE);
                selectedVote = null;
            }
        } else if (currentPhase == Phase.RESULTS && viewModel.isHost()) {
            startNextMultiplayerRound();
        } else if (currentPhase == Phase.FINISHED) {
            finish();
        }
    }

    private void handleLocalAction() {
        Phase currentPhase = viewModel.getCurrentPhase().getValue();
        if (currentPhase == Phase.RESULTS) {
            viewModel.startNewRoundLocal();
        } else if (currentPhase == Phase.FINISHED) {
            finish();
        } else if (currentPhase == Phase.REVIEW) {
            if (!viewModel.getLocalMatchedAnswers().isEmpty() || !viewModel.getLocalDeletedPlayerIndices().isEmpty()) {
                viewModel.calculateLocalScores();
            } else {
                viewModel.startVotingLocal();
            }
        } else if (currentPhase == Phase.VOTING) {
            if (selectedVote != null) {
                viewModel.submitLocalVote(selectedVote);
                selectedVote = null;
            }
        }
    }

    private void startVotingLocal() {
        viewModel.startVotingLocal();
    }

    private void submitMultiplayerAnswer() {
        String answer = binding.editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;
        viewModel.submitMultiplayerAnswer(answer);
        binding.editTextAnswer.setText("");
        binding.layoutAnswerInput.setVisibility(View.GONE);

        binding.textViewPhaseTitle.setText(R.string.waiting_for_others);
    }

    private void startNextMultiplayerRound() {
        if (viewModel.getCurrentPhase().getValue() == Phase.FINISHED) {
            finish();
            return;
        }
        
        // Clear local match state before next round
        multiplayerMatchedAnswers.clear();

        viewModel.startNextMultiplayerRound();
    }

    private void startVotingPhase() {
        if (!multiplayerMatchedAnswers.isEmpty()) {
            viewModel.calculateMultiplayerScores(multiplayerMatchedAnswers);
        } else {
            viewModel.updateMultiplayerStatus(RoomStatus.VOTING);
        }
    }
    private void showScoreSheet() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.scoreboard_desc)).append("\n\n");

        List<Player> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));

        for (Player p : sortedPlayers) {
            sb.append(p.getName()).append(": ").append(p.getScore()).append("\n");
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(sb.toString().trim());

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding / 2, padding, padding / 2);
        textView.setTextSize(16f);
        textView.setTextColor(getColor(android.R.color.white));

        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_scoreboard_title)
                .setView(scrollView) // Use the ScrollView instead of setMessage!
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showLogs() {
        List<String> logs = viewModel.getLogs().getValue();
        if (logs == null || logs.isEmpty()) {
            Toast.makeText(this, R.string.error_no_logs, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            // Added an extra newline so logs are spaced out and easier to read
            sb.append("• ").append(log).append("\n\n");
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(sb.toString().trim());

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        textView.setPadding(padding, padding / 2, padding, padding / 2);
        textView.setTextSize(15f);
        textView.setTextColor(getColor(android.R.color.white));

        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_logs_title)
                .setView(scrollView) // Use the ScrollView instead of setMessage!
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_leave_game_title)
                .setMessage(R.string.dialog_leave_game_message)
                .setPositiveButton(R.string.leave, (dialog, which) -> {
                    if (isMultiplayer) {
                        viewModel.leaveMultiplayerRoom();
                    }
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void prepareReviewUI(GameRoom room) {
        Map<String, String> allGuesses = room.getGuesses();
        String spotlightId = room.getSpotlightPlayerId();
        final List<String> choices = new ArrayList<>();
        for (Map.Entry<String, String> entry : allGuesses.entrySet()) {
            if (!entry.getKey().equals(spotlightId)) {
                choices.add(entry.getValue());
            }
        }
        Collections.shuffle(choices);
        
        final AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(choices, null);
        adapter.setListener(new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {}

            @Override
            public void onMatchClicked(String choice, int position) {
                if (multiplayerMatchedAnswers.contains(choice)) {
                    multiplayerMatchedAnswers.remove(choice);
                } else {
                    multiplayerMatchedAnswers.add(choice);
                }
                binding.buttonAction.setText(multiplayerMatchedAnswers.isEmpty() ? getString(R.string.button_start_voting) : getString(R.string.button_reveal_results));
                
                adapter.setMatchedAnswers(multiplayerMatchedAnswers);
            }
        });
        adapter.setReviewMode(true);
        
        // Restore previous matches if any (e.g. UI recreation)
        adapter.setMatchedAnswers(multiplayerMatchedAnswers);

        binding.recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewChoices.setAdapter(adapter);
    }

    private void prepareLocalReviewUI() {
        binding.layoutSelection.setVisibility(View.VISIBLE);
        binding.textViewSelectionPrompt.setText(R.string.review_prompt);
        binding.buttonAction.setVisibility(View.VISIBLE);
        binding.buttonAction.setText(viewModel.getLocalMatchedAnswers().isEmpty() ? R.string.button_start_voting : R.string.button_reveal_results);
        binding.buttonAction.setEnabled(true);
        
        List<String> choices = viewModel.getCurrentChoices().getValue();
        final AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(choices, null);
        adapter.setListener(new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {}

            @Override
            public void onMatchClicked(String choice, int position) {
                viewModel.toggleLocalMatch(choice);
                binding.buttonAction.setText(viewModel.getLocalMatchedAnswers().isEmpty() ? R.string.button_start_voting : R.string.button_reveal_results);
                adapter.setMatchedAnswers(viewModel.getLocalMatchedAnswers());
            }

            @Override
            public void onDeleteClicked(int position) {
                viewModel.deleteLocalChoice(position);
                binding.buttonAction.setText((viewModel.getLocalMatchedAnswers().isEmpty() && viewModel.getLocalDeletedPlayerIndices().isEmpty()) ? R.string.button_start_voting : R.string.button_reveal_results);
                adapter.notifyItemChanged(position);
            }
        });
        adapter.setReviewMode(true);
        adapter.setMatchedAnswers(viewModel.getLocalMatchedAnswers());
        binding.recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewChoices.setAdapter(adapter);
    }

    private void prepareLocalVotingUI() {
        binding.layoutSelection.setVisibility(View.VISIBLE);
        binding.textViewSelectionPrompt.setText(getString(R.string.voting_prompt_format, players.get(viewModel.getCurrentPlayerIndex()).getName()));
        binding.buttonAction.setVisibility(View.VISIBLE);
        binding.buttonAction.setText(R.string.button_confirm_vote);
        binding.buttonAction.setEnabled(false);
        
        List<String> choices = new ArrayList<>(viewModel.getCurrentChoices().getValue());
        String playerAnswer = viewModel.getLocalAnswers().get(viewModel.getCurrentPlayerIndex());
        choices.remove(playerAnswer);
        choices.add(viewModel.getLocalAnswers().get(viewModel.getSpotlightPlayerIndex().getValue()));
        Collections.shuffle(choices);

        AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(choices, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {
                selectedVote = choice;
                binding.buttonAction.setEnabled(true);
            }
        });
        binding.recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewChoices.setAdapter(adapter);
    }

    private void showLocalResults() {
        List<Player> playerList = new ArrayList<>(players);
        Collections.sort(playerList, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        binding.recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewResults.setAdapter(new ResultAdapter(playerList));
    }

    private void prepareMultiplayerVotingUI(GameRoom room) {
        binding.layoutSelection.setVisibility(View.VISIBLE);
        binding.buttonAction.setVisibility(View.VISIBLE);
        binding.buttonAction.setText(R.string.button_confirm_vote);
        binding.buttonAction.setEnabled(false);

        Map<String, String> guesses = room.getGuesses();
        List<String> choices = new ArrayList<>();
        if (guesses != null) {
            for (Map.Entry<String, String> entry : guesses.entrySet()) {
                if (!entry.getKey().equals(playerId)) {
                    choices.add(entry.getValue());
                }
            }
        }
        Collections.shuffle(choices);

        AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(choices, new AnswerChoiceAdapter.OnChoiceActionListener() {
            @Override
            public void onChoiceSelected(String choice) {
                selectedVote = choice;
                binding.buttonAction.setEnabled(true);
            }
        });

        binding.recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewChoices.setAdapter(adapter);
    }

    private void showMultiplayerResultsUI(GameRoom room) {
        List<Player> playerList = new ArrayList<>(room.getPlayers().values());
        Collections.sort(playerList, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        binding.recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewResults.setAdapter(new ResultAdapter(playerList));
    }

    private void setupLottieView(LottieAnimationView view) {
        view.setRenderMode(RenderMode.HARDWARE);
    }

    private void showConfettiAnimation() {
        binding.animationViewConfetti.setVisibility(View.VISIBLE);
        binding.animationViewConfetti.playAnimation();
    }

    private void showWinner(List<Player> playerList) {
        if (playerList == null || playerList.isEmpty()) return;
        
        Player winner = playerList.get(0);
        for (Player p : playerList) {
            if (p.getScore() > winner.getScore()) {
                winner = p;
            }
        }
        
        binding.textViewWinner.setText(getString(R.string.winner_announcement, winner.getName()));
        binding.textViewWinner.setVisibility(View.VISIBLE);
        
        // Hide other distracting elements
        binding.cardViewQuestion.setVisibility(View.GONE);
        binding.textViewTargetPlayer.setVisibility(View.GONE);
        binding.viewSpotlightAvatar.setVisibility(View.GONE);
    }
}
