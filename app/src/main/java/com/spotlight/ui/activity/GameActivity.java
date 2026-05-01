package com.spotlight.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spotlight.R;
import com.spotlight.databinding.ActivityGameBinding;
import com.spotlight.logic.GameViewModel;
import com.spotlight.logic.GameViewModel.Phase;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);

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
        // THE FIX: Isolate Local Phase changes so they don't cross-trigger Multiplayer Logic
        viewModel.getCurrentPhase().observe(this, phase -> {
            if (phase != null && !isMultiplayer) {
                updateUI(phase);
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
                Phase phase = viewModel.getCurrentPhase().getValue();
                if (phase != null) {
                    updateUI(phase);
                }
            }
        });

        viewModel.getPlayers().observe(this, playerList -> {
            if (playerList != null) {
                this.players = playerList;
            }
        });

        // THE FIX: Let Firebase directly drive the Multiplayer UI state
        if (isMultiplayer) {
            viewModel.getRoomData().observe(this, room -> {
                if (room != null) {
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
        String status = room.getStatus();
        String spotlightId = room.getSpotlightPlayerId();

        if (status == null) return;

        if (room.getPlayers() != null) {
            viewModel.setPlayers(new ArrayList<>(room.getPlayers().values()));
        }

        if (room.getLogs() != null) {
            viewModel.setLogs(room.getLogs());
        }

        Phase phase = Phase.WAITING_FOR_ANSWERS;
        if ("REVIEW".equals(status)) phase = Phase.REVIEW;
        else if ("VOTING".equals(status)) phase = Phase.VOTING;
        else if ("RESULTS".equals(status)) phase = Phase.RESULTS;
        else if ("FINISHED".equals(status)) phase = Phase.FINISHED;

        if (phase == Phase.WAITING_FOR_ANSWERS && viewModel.isHost()) {
            Map<String, String> guesses = room.getGuesses();
            if (guesses != null && guesses.size() == room.getPlayers().size() && room.getPlayers().size() > 0) {
                viewModel.updateMultiplayerStatus(RoomStatus.REVIEW);
                phase = Phase.REVIEW;
            }
        }

        if (phase == Phase.VOTING && viewModel.isHost()) {
            Map<String, String> votes = room.getVotes();
            Map<String, Player> playersMap = room.getPlayers();
            if (votes != null && votes.size() == playersMap.size() - 1 && playersMap.size() > 1) {
                viewModel.calculateMultiplayerScores(null);
                phase = Phase.RESULTS;
            }
        }

        if (spotlightId != null) {
            Player spotlightPlayer = room.getPlayers().get(spotlightId);
            if (spotlightPlayer != null) {
                if (room.getCurrentQuestion() != null) {
                    binding.textViewQuestion.setText(room.getCurrentQuestion());
                    viewModel.setCurrentQuestion(room.getCurrentQuestion());
                }
                viewModel.setSpotlightPlayerId(spotlightId);

                // THE FIX: We pass the 'phase' directly to the renderer so it doesn't have to wait for LiveData!
                updateMultiplayerUI(room, spotlightPlayer, phase);

                updateSpotlightAvatar(spotlightPlayer);
            }
        }
    }

    // THE FIX: Added Phase directly to the parameters!
    private void updateMultiplayerUI(GameRoom room, Player spotlightPlayer, Phase phase) {
        if (phase == null) return;

        binding.layoutAnswerInput.setVisibility(View.GONE);
        binding.layoutSelection.setVisibility(View.GONE);
        binding.layoutPassDevice.setVisibility(View.GONE);
        binding.layoutResults.setVisibility(View.GONE);
        binding.buttonAction.setVisibility(View.GONE);
        binding.textViewReviewInstructions.setVisibility(View.GONE);

        boolean isSpotlight = playerId.equals(spotlightPlayer.getId());

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                binding.textViewPhaseTitle.setText(isSpotlight ? "Your Secret Answer" : "Guess the Answer");
                binding.textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, spotlightPlayer.getName()));

                Map<String, String> currentGuesses = room.getGuesses();
                if (currentGuesses == null || !currentGuesses.containsKey(playerId)) {
                    binding.layoutAnswerInput.setVisibility(View.VISIBLE);
                    binding.editTextAnswer.setHint(isSpotlight ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
                } else {
                    binding.textViewPhaseTitle.setText("Waiting for others...");
                }
                break;

            case REVIEW:
                binding.textViewPhaseTitle.setText("Review Phase");
                if (isSpotlight) {
                    binding.layoutSelection.setVisibility(View.VISIBLE);
                    binding.textViewReviewInstructions.setVisibility(View.VISIBLE);
                    String secret = room.getGuesses().get(playerId);
                    binding.textViewSelectionPrompt.setText(getString(R.string.your_answer_format, (secret != null ? secret : "")));
                    prepareReviewUI(room);
                    binding.buttonAction.setVisibility(View.VISIBLE);
                    binding.buttonAction.setEnabled(true);
                    binding.buttonAction.setText(multiplayerMatchedAnswers.isEmpty() ? getString(R.string.button_start_voting) : getString(R.string.button_reveal_results));
                } else {
                    binding.textViewPhaseTitle.setText(spotlightPlayer.getName() + " is reviewing...");
                }
                break;

            case VOTING:
                binding.textViewPhaseTitle.setText(R.string.phase_voting);
                if (isSpotlight) {
                    binding.textViewPhaseTitle.setText("Wait for votes...");
                } else {
                    Map<String, String> currentVotes = room.getVotes();
                    if (currentVotes == null || !currentVotes.containsKey(playerId)) {
                        binding.layoutSelection.setVisibility(View.VISIBLE);
                        binding.textViewSelectionPrompt.setText(R.string.selection_prompt);
                        prepareMultiplayerVotingUI(room);
                    } else {
                        binding.textViewPhaseTitle.setText("Waiting for others...");
                    }
                }
                break;

            case RESULTS:
                binding.textViewPhaseTitle.setText(R.string.phase_round_results);
                binding.layoutResults.setVisibility(View.VISIBLE);
                showMultiplayerResultsUI(room);
                binding.buttonAction.setVisibility(View.VISIBLE);
                binding.buttonAction.setText(viewModel.isHost() ? getString(R.string.button_next_round) : "Wait for host...");
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

        if (com.spotlight.util.ProfanityFilter.containsProfanity(answer)) {
            Toast.makeText(this, "Please keep answers appropriate!", Toast.LENGTH_SHORT).show();
            return; // Abort submission, let them try again!
        }
        viewModel.submitLocalAnswer(answer);
        binding.editTextAnswer.setText("");
    }

    private void handleMultiplayerAction() {
        // Since button pushes rely on the current state, we derive it from the UI or Phase
        Phase currentPhase = viewModel.getCurrentPhase().getValue();
        if (currentPhase == null) return;

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

        if (com.spotlight.util.ProfanityFilter.containsProfanity(answer)) {
            Toast.makeText(this, "Please keep answers appropriate!", Toast.LENGTH_SHORT).show();
            return; // Abort submission, let them try again!
        }

        viewModel.submitMultiplayerAnswer(answer);
        binding.editTextAnswer.setText("");
        binding.layoutAnswerInput.setVisibility(View.GONE);
    }

    private void startNextMultiplayerRound() {
        if (viewModel.getCurrentPhase().getValue() == Phase.FINISHED) {
            finish();
            return;
        }
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
        sb.append(getString(R.string.scoreboard_desc));
        List<Player> sortedPlayers = new ArrayList<>(players);
        Collections.sort(sortedPlayers, (p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()));
        for (Player p : sortedPlayers) {
            sb.append(p.getName()).append(": ").append(p.getScore()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_scoreboard_title)
                .setMessage(sb.toString())
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
            sb.append("- ").append(log).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_logs_title)
                .setMessage(sb.toString())
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
        if (allGuesses != null) {
            for (Map.Entry<String, String> entry : allGuesses.entrySet()) {
                if (!entry.getKey().equals(spotlightId)) {
                    choices.add(entry.getValue());
                }
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
        binding.cardViewQuestion.setVisibility(View.GONE);
        binding.textViewTargetPlayer.setVisibility(View.GONE);
        binding.viewSpotlightAvatar.setVisibility(View.GONE);
    }
}