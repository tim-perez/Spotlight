package com.spotlight;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.logic.QuestionRepository;
import com.spotlight.model.Player;
import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameActivity extends AppCompatActivity {

    private enum Phase {
        PASS_TO_SPOTLIGHT,
        SPOTLIGHT_INPUT,
        PASS_TO_GUESSER,
        GUESSER_INPUT,
        VOTING_PASS,
        VOTING,
        RESULTS
    }

    private List<Player> players;
    private int spotlightPlayerIndex = 0;
    private int currentGuesserIndex = -1;
    private Phase currentPhase;
    private QuestionRepository questionRepository;
    private Question currentQuestion;
    
    private String secretAnswer;
    private Map<Player, String> playerGuesses = new HashMap<>();
    private Map<Player, String> playerVotes = new HashMap<>();
    private List<String> allAnswerOptions = new ArrayList<>();

    private TextView textViewPhaseTitle, textViewTargetPlayer, textViewQuestion, textViewPassTo, textViewSelectionPrompt;
    private LinearLayout layoutAnswerInput, layoutSelection, layoutPassDevice, layoutResults;
    private EditText editTextAnswer;
    private Button buttonSubmitAnswer, buttonReady, buttonAction;
    private RecyclerView recyclerViewChoices, recyclerViewResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        players = (ArrayList<Player>) getIntent().getSerializableExtra("players");
        if (players == null || players.isEmpty()) {
            finish();
            return;
        }

        questionRepository = new QuestionRepository();
        initViews();
        
        startNewRound();
    }

    private void initViews() {
        textViewPhaseTitle = findViewById(R.id.textViewPhaseTitle);
        textViewTargetPlayer = findViewById(R.id.textViewTargetPlayer);
        textViewQuestion = findViewById(R.id.textViewQuestion);
        textViewPassTo = findViewById(R.id.textViewPassTo);
        textViewSelectionPrompt = findViewById(R.id.textViewSelectionPrompt);

        layoutAnswerInput = findViewById(R.id.layoutAnswerInput);
        layoutSelection = findViewById(R.id.layoutSelection);
        layoutPassDevice = findViewById(R.id.layoutPassDevice);
        layoutResults = findViewById(R.id.layoutResults);

        editTextAnswer = findViewById(R.id.editTextAnswer);
        buttonSubmitAnswer = findViewById(R.id.buttonSubmitAnswer);
        buttonReady = findViewById(R.id.buttonReady);
        buttonAction = findViewById(R.id.buttonAction);

        recyclerViewChoices = findViewById(R.id.recyclerViewChoices);
        recyclerViewResults = findViewById(R.id.recyclerViewResults);

        buttonSubmitAnswer.setOnClickListener(v -> handleSubmitAnswer());
        buttonReady.setOnClickListener(v -> handleReady());
        buttonAction.setOnClickListener(v -> handleAction());
    }

    private void startNewRound() {
        currentQuestion = questionRepository.getRandomQuestion();
        playerGuesses.clear();
        playerVotes.clear();
        secretAnswer = null;
        currentGuesserIndex = -1;
        
        setPhase(Phase.PASS_TO_SPOTLIGHT);
    }

    private void setPhase(Phase phase) {
        currentPhase = phase;
        updateUI();
    }

    private void updateUI() {
        // Hide all layouts first
        layoutAnswerInput.setVisibility(View.GONE);
        layoutSelection.setVisibility(View.GONE);
        layoutPassDevice.setVisibility(View.GONE);
        layoutResults.setVisibility(View.GONE);
        buttonAction.setVisibility(View.GONE);

        Player spotlightPlayer = players.get(spotlightPlayerIndex);

        switch (currentPhase) {
            case PASS_TO_SPOTLIGHT:
                layoutPassDevice.setVisibility(View.VISIBLE);
                textViewPassTo.setText("Pass the device to " + spotlightPlayer.getName() + "\n(Spotlight Player)");
                textViewPhaseTitle.setText("New Round");
                textViewTargetPlayer.setText("");
                textViewQuestion.setText("Keep the screen away from others!");
                break;

            case SPOTLIGHT_INPUT:
                layoutAnswerInput.setVisibility(View.VISIBLE);
                textViewPhaseTitle.setText("Spotlight Input");
                textViewTargetPlayer.setText(spotlightPlayer.getName() + " is in the Spotlight");
                textViewQuestion.setText(currentQuestion.getText());
                editTextAnswer.setText("");
                editTextAnswer.setHint("Enter your secret answer");
                break;

            case PASS_TO_GUESSER:
                layoutPassDevice.setVisibility(View.VISIBLE);
                Player guesser = getNextGuesser();
                textViewPassTo.setText("Pass the device to " + guesser.getName());
                textViewPhaseTitle.setText("Guessing Phase");
                textViewTargetPlayer.setText("");
                textViewQuestion.setText("Try to guess " + spotlightPlayer.getName() + "'s answer!");
                break;

            case GUESSER_INPUT:
                layoutAnswerInput.setVisibility(View.VISIBLE);
                Player currentGuesser = players.get(currentGuesserIndex);
                textViewPhaseTitle.setText("Guessing Phase");
                textViewTargetPlayer.setText(currentGuesser.getName() + "'s Turn");
                textViewQuestion.setText(currentQuestion.getText());
                editTextAnswer.setText("");
                editTextAnswer.setHint("What did " + spotlightPlayer.getName() + " write?");
                break;

            case VOTING_PASS:
                layoutPassDevice.setVisibility(View.VISIBLE);
                textViewPassTo.setText("Pass the device back to " + spotlightPlayer.getName());
                textViewPhaseTitle.setText("Voting Phase Prep");
                textViewTargetPlayer.setText("");
                textViewQuestion.setText("No one guessed it exactly! Time for the voting phase.");
                break;

            case VOTING:
                layoutSelection.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText("Next Voter / Reveal");
                textViewPhaseTitle.setText("Voting Phase");
                // We'll handle individual voting internally
                prepareVotingUI();
                break;

            case RESULTS:
                layoutResults.setVisibility(View.VISIBLE);
                buttonAction.setVisibility(View.VISIBLE);
                buttonAction.setText("Next Round");
                textViewPhaseTitle.setText("Round Results");
                showResultsUI();
                break;
        }
    }

    private Player getNextGuesser() {
        int nextIndex = (currentGuesserIndex == -1) ? 0 : currentGuesserIndex + 1;
        while (nextIndex < players.size()) {
            if (nextIndex != spotlightPlayerIndex) {
                currentGuesserIndex = nextIndex;
                return players.get(nextIndex);
            }
            nextIndex++;
        }
        return null;
    }

    private void handleReady() {
        if (currentPhase == Phase.PASS_TO_SPOTLIGHT) {
            setPhase(Phase.SPOTLIGHT_INPUT);
        } else if (currentPhase == Phase.PASS_TO_GUESSER) {
            setPhase(Phase.GUESSER_INPUT);
        } else if (currentPhase == Phase.VOTING_PASS) {
            setupVotingOptions();
            currentGuesserIndex = -1;
            startNextVoter();
        }
    }

    private void handleSubmitAnswer() {
        String answer = editTextAnswer.getText().toString().trim();
        if (answer.isEmpty()) {
            Toast.makeText(this, "Please enter an answer", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPhase == Phase.SPOTLIGHT_INPUT) {
            secretAnswer = answer;
            currentGuesserIndex = -1;
            setPhase(Phase.PASS_TO_GUESSER);
        } else if (currentPhase == Phase.GUESSER_INPUT) {
            Player currentGuesser = players.get(currentGuesserIndex);
            playerGuesses.put(currentGuesser, answer);
            
            // Check for exact match (case insensitive)
            if (answer.equalsIgnoreCase(secretAnswer)) {
                handleImmediateWin();
            } else {
                if (getNextGuesser() != null) {
                    setPhase(Phase.PASS_TO_GUESSER);
                } else {
                    // Everyone has guessed, no one got it exactly
                    setPhase(Phase.VOTING_PASS);
                }
            }
        }
    }

    private void handleImmediateWin() {
        // Find everyone who guessed correctly this round
        // Although in this turn-based flow, we might stop at the first one.
        // User said: "If the player in the "Spotlight" notices someone guess their answer correctly, the round automatically ends"
        // Let's award +4 to the current guesser and move to results.
        Player winner = players.get(currentGuesserIndex);
        winner.addScore(4);
        
        Toast.makeText(this, winner.getName() + " guessed it EXACTLY! +4 points", Toast.LENGTH_LONG).show();
        setPhase(Phase.RESULTS);
    }

    private void setupVotingOptions() {
        allAnswerOptions.clear();
        allAnswerOptions.add(secretAnswer);
        for (String guess : playerGuesses.values()) {
            if (!allAnswerOptions.contains(guess)) {
                allAnswerOptions.add(guess);
            }
        }
        Collections.shuffle(allAnswerOptions);
    }

    private void startNextVoter() {
        Player nextVoter = getNextGuesser();
        if (nextVoter != null) {
            setPhase(Phase.VOTING);
            textViewTargetPlayer.setText(nextVoter.getName() + "'s Vote");
            textViewSelectionPrompt.setText("Which answer did " + players.get(spotlightPlayerIndex).getName() + " write?");
        } else {
            calculateVotingScores();
            setPhase(Phase.RESULTS);
        }
    }

    private void prepareVotingUI() {
        recyclerViewChoices.setLayoutManager(new LinearLayoutManager(this));
        AnswerChoiceAdapter adapter = new AnswerChoiceAdapter(allAnswerOptions, choice -> {
            Player voter = players.get(currentGuesserIndex);
            playerVotes.put(voter, choice);
        });
        recyclerViewChoices.setAdapter(adapter);
    }

    private void handleAction() {
        if (currentPhase == Phase.VOTING) {
            Player voter = players.get(currentGuesserIndex);
            if (!playerVotes.containsKey(voter)) {
                Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show();
                return;
            }
            startNextVoter();
        } else if (currentPhase == Phase.RESULTS) {
            if (checkForWinner()) {
                finish();
            } else {
                spotlightPlayerIndex = (spotlightPlayerIndex + 1) % players.size();
                startNewRound();
            }
        }
    }

    private void calculateVotingScores() {
        Player spotlight = players.get(spotlightPlayerIndex);
        for (Map.Entry<Player, String> entry : playerVotes.entrySet()) {
            Player voter = entry.getKey();
            String vote = entry.getValue();

            if (vote.equalsIgnoreCase(secretAnswer)) {
                // Correct guess: +2 for picker
                voter.addScore(2);
                // Spotlight gets +1 for each correct guess
                spotlight.addScore(1);
            } else {
                // Picker picked someone else's answer
                // Find who wrote this decoy answer
                for (Map.Entry<Player, String> guessEntry : playerGuesses.entrySet()) {
                    if (guessEntry.getValue().equalsIgnoreCase(vote)) {
                        // +1 for the person who wrote the decoy (if it's not the spotlight, which it isn't here)
                        guessEntry.getKey().addScore(1);
                        // Assuming only one person gets points if multiple wrote the same guess? 
                        // Usually in these games, all who wrote it get a point.
                    }
                }
            }
        }
    }

    private void showResultsUI() {
        recyclerViewResults.setLayoutManager(new LinearLayoutManager(this));
        // Simple list of players and scores for now
        ResultAdapter adapter = new ResultAdapter(players);
        recyclerViewResults.setAdapter(adapter);
        
        textViewQuestion.setText("The secret answer was: " + secretAnswer);
    }

    private boolean checkForWinner() {
        for (Player p : players) {
            if (p.getScore() >= 25) {
                Toast.makeText(this, p.getName() + " WINS THE GAME!", Toast.LENGTH_LONG).show();
                return true;
            }
        }
        return false;
    }
}
