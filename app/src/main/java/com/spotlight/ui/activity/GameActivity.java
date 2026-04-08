package com.spotlight.ui.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.RenderMode;
import com.spotlight.R;
import com.spotlight.databinding.ActivityGameBinding;
import com.spotlight.logic.GameViewModel;
import com.spotlight.logic.GameViewModel.Phase;
import com.spotlight.model.Player;
import com.spotlight.ui.adapter.AnswerChoiceAdapter;
import com.spotlight.ui.adapter.ResultAdapter;
import com.spotlight.util.AvatarUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameActivity extends AppCompatActivity {

    private ActivityGameBinding binding;
    private GameViewModel viewModel;

    private boolean isMultiplayer;
    private String roomCode;
    private String playerId;
    private String hostId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);

        initFromIntent();
        setupObservers();
        initViews();
    }

    private void initFromIntent() {
        isMultiplayer = getIntent().getBooleanExtra("isMultiplayer", false);
        roomCode = getIntent().getStringExtra("roomCode");
        playerId = getIntent().getStringExtra("playerId");
        hostId = getIntent().getStringExtra("hostId");
        String category = getIntent().getStringExtra("category");
        List<Player> initialPlayers = (List<Player>) getIntent().getSerializableExtra("players");

        viewModel.init(isMultiplayer, roomCode, playerId, hostId, initialPlayers, category);
    }

    private void setupObservers() {
        viewModel.getCurrentPhase().observe(this, phase -> {
            if (phase != null) {
                updateLayoutVisibility(phase);
            }
        });

        viewModel.getCurrentQuestion().observe(this, question -> {
            if (question != null) {
                binding.textViewQuestion.setText(question.getText());
            }
        });

        viewModel.getPhaseTitle().observe(this, title -> {
            if (title != null) {
                binding.textViewPhaseTitle.setText(title);
            }
        });

        viewModel.getSpotlightPlayerName().observe(this, name -> {
            if (name != null) {
                binding.textViewTargetPlayer.setText(getString(R.string.spotlight_announcement, name));
            }
        });

        viewModel.getSpotlightAvatarColor().observe(this, color -> {
            if (color != null) {
                AvatarUtils.setAvatarColor(binding.viewSpotlightAvatar, color);
                binding.viewSpotlightAvatar.setVisibility(color != 0 ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getSelectionPrompt().observe(this, prompt -> {
            binding.textViewSelectionPrompt.setText(prompt);
        });

        viewModel.getActionButtonText().observe(this, text -> {
            binding.buttonAction.setText(text);
        });

        viewModel.getActionButtonEnabled().observe(this, enabled -> {
            binding.buttonAction.setEnabled(Boolean.TRUE.equals(enabled));
        });

        viewModel.getPassToPlayerName().observe(this, name -> {
            if (name != null) {
                binding.textViewPassTo.setText(getString(R.string.pass_device_to, name));
            }
        });

        viewModel.getPassToAvatarColor().observe(this, color -> {
            if (color != null) {
                AvatarUtils.setAvatarColor(binding.viewPassToAvatar, color);
                binding.viewPassToAvatar.setVisibility(color != 0 ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getShowPassDevice().observe(this, show -> {
            binding.layoutPassDevice.setVisibility(Boolean.TRUE.equals(show) ? View.VISIBLE : View.GONE);
        });

        viewModel.getIsWaitingForOthers().observe(this, waiting -> {
            if (Boolean.TRUE.equals(waiting)) {
                binding.layoutAnswerInput.setVisibility(View.GONE);
                binding.layoutSelection.setVisibility(View.GONE);
                binding.textViewQuestion.setText(R.string.waiting_for_others);
            }
        });

        viewModel.getCurrentChoices().observe(this, choices -> {
            if (choices == null) return;
            Phase phase = viewModel.getCurrentPhase().getValue();
            if (phase == Phase.REVIEW || phase == Phase.VOTING) {
                if (binding.recyclerViewChoices.getAdapter() instanceof AnswerChoiceAdapter) {
                    AnswerChoiceAdapter adapter = (AnswerChoiceAdapter) binding.recyclerViewChoices.getAdapter();
                    adapter.setChoices(choices);
                    adapter.setReviewMode(phase == Phase.REVIEW);
                } else {
                    updateChoicesAdapter(choices, phase);
                }
            }
        });

        viewModel.getMatchedAnswers().observe(this, matched -> {
            if (binding.recyclerViewChoices.getAdapter() instanceof AnswerChoiceAdapter) {
                ((AnswerChoiceAdapter) binding.recyclerViewChoices.getAdapter()).setMatchedAnswers(matched);
            }
        });

        viewModel.getSortedPlayers().observe(this, sortedList -> {
            if (sortedList != null) {
                binding.recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
                binding.recyclerViewResults.setAdapter(new ResultAdapter(sortedList));
            }
        });

        viewModel.getWinnerName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                binding.textViewWinner.setText(getString(R.string.winner_announcement, name));
                binding.textViewWinner.setVisibility(View.VISIBLE);
                binding.cardViewQuestion.setVisibility(View.GONE);
                binding.textViewTargetPlayer.setVisibility(View.GONE);
                binding.viewSpotlightAvatar.setVisibility(View.GONE);
                showConfettiAnimation();
            } else {
                binding.textViewWinner.setVisibility(View.GONE);
            }
        });

        if (isMultiplayer) {
            viewModel.getRoomData().observe(this, room -> {
                if (room != null) {
                    viewModel.processRoomUpdate(room);
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

        binding.buttonSubmitAnswer.setOnClickListener(v -> handleSubmitAnswer());
        binding.buttonReady.setOnClickListener(v -> viewModel.hidePassDevice());
        binding.buttonAction.setOnClickListener(v -> handleAction());
    }

    private void updateLayoutVisibility(Phase phase) {
        binding.layoutAnswerInput.setVisibility(View.GONE);
        binding.layoutSelection.setVisibility(View.GONE);
        binding.layoutResults.setVisibility(View.GONE);
        binding.buttonAction.setVisibility(View.GONE);
        binding.textViewReviewInstructions.setVisibility(View.GONE);
        
        // Reset question text if it was overridden by "Waiting for others"
        if (viewModel.getCurrentQuestion().getValue() != null) {
            binding.textViewQuestion.setText(viewModel.getCurrentQuestion().getValue().getText());
        }

        boolean isSpotlight = viewModel.isSpotlight();
        boolean waiting = Boolean.TRUE.equals(viewModel.getIsWaitingForOthers().getValue());

        switch (phase) {
            case WAITING_FOR_ANSWERS:
                if (!waiting) {
                    binding.layoutAnswerInput.setVisibility(View.VISIBLE);
                    binding.editTextAnswer.setHint(isSpotlight ? getString(R.string.hint_secret_answer) : getString(R.string.hint_guess_answer));
                } else {
                    binding.textViewQuestion.setText(R.string.waiting_for_others);
                }
                break;

            case REVIEW:
                if (isSpotlight) {
                    binding.layoutSelection.setVisibility(View.VISIBLE);
                    binding.textViewReviewInstructions.setVisibility(View.VISIBLE);
                    binding.buttonAction.setVisibility(View.VISIBLE);
                } else {
                    binding.textViewQuestion.setText(R.string.spotlight_reviewing);
                }
                break;

            case VOTING:
                if (isSpotlight) {
                    binding.textViewQuestion.setText(R.string.wait_for_votes);
                } else {
                    if (!waiting) {
                        binding.layoutSelection.setVisibility(View.VISIBLE);
                        binding.buttonAction.setVisibility(View.VISIBLE);
                    } else {
                        binding.textViewQuestion.setText(R.string.waiting_for_others);
                    }
                }
                break;

            case RESULTS:
            case FINISHED:
                binding.layoutResults.setVisibility(View.VISIBLE);
                binding.buttonAction.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateChoicesAdapter(List<String> choices, Phase phase) {
        AnswerChoiceAdapter adapter;
        if (phase == Phase.REVIEW) {
            adapter = new AnswerChoiceAdapter(choices, new AnswerChoiceAdapter.OnChoiceActionListener() {
                @Override
                public void onChoiceSelected(String choice) {}
                @Override
                public void onMatchClicked(String choice, int position) {
                    viewModel.toggleMatch(choice);
                }
                @Override
                public void onDeleteClicked(int position) {
                    viewModel.deleteChoice(position);
                }
            });
            adapter.setReviewMode(true);
            adapter.setMatchedAnswers(viewModel.getMatchedAnswers().getValue());
        } else {
            adapter = new AnswerChoiceAdapter(choices, new AnswerChoiceAdapter.OnChoiceActionListener() {
                @Override
                public void onChoiceSelected(String choice) {
                    viewModel.setSelectedVote(choice);
                }
            });
        }
        binding.recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewChoices.setAdapter(adapter);
    }

    private void handleSubmitAnswer() {
        String answer = binding.editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;

        viewModel.submitAnswer(answer);
        binding.editTextAnswer.setText("");
    }

    private void handleAction() {
        if (viewModel.getCurrentPhase().getValue() == Phase.FINISHED) {
            finish();
        } else {
            viewModel.handleAction();
        }
    }

    private void showScoreSheet() {
        List<Player> players = viewModel.getPlayers().getValue();
        if (players == null) return;

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

    private void setupLottieView(LottieAnimationView view) {
        view.setRenderMode(RenderMode.HARDWARE);
    }

    private void showConfettiAnimation() {
        binding.animationViewConfetti.setVisibility(View.VISIBLE);
        binding.animationViewConfetti.playAnimation();
    }
}
