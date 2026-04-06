package com.spotlight;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.logic.QuestionRepository;
import com.spotlight.model.Player;
import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity {

    private List<Player> players;
    private int currentPlayerIndex = 0;
    private QuestionRepository questionRepository;
    private GuesserAdapter guesserAdapter;

    private TextView textViewSpotlightPlayer;
    private TextView textViewQuestion;

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

        textViewSpotlightPlayer = findViewById(R.id.textViewSpotlightPlayer);
        textViewQuestion = findViewById(R.id.textViewQuestion);
        RecyclerView recyclerViewGuessers = findViewById(R.id.recyclerViewGuessers);
        Button buttonNextTurn = findViewById(R.id.buttonNextTurn);

        recyclerViewGuessers.setLayoutManager(new LinearLayoutManager(this));
        // Pass a list excluding the current player to the guesser adapter
        updateGuesserAdapter();
        recyclerViewGuessers.setAdapter(guesserAdapter);

        buttonNextTurn.setOnClickListener(v -> nextTurn());

        loadTurn();
    }

    private void updateGuesserAdapter() {
        List<Player> guessers = new ArrayList<>(players);
        guessers.remove(currentPlayerIndex);
        if (guesserAdapter == null) {
            guesserAdapter = new GuesserAdapter(guessers);
        } else {
            // This is a bit simplified for now
            guesserAdapter = new GuesserAdapter(guessers);
        }
    }

    private void loadTurn() {
        Player spotlightPlayer = players.get(currentPlayerIndex);
        textViewSpotlightPlayer.setText(spotlightPlayer.getName());

        Question question = questionRepository.getRandomQuestion();
        textViewQuestion.setText(question.getText());

        updateGuesserAdapter();
        RecyclerView recyclerViewGuessers = findViewById(R.id.recyclerViewGuessers);
        recyclerViewGuessers.setAdapter(guesserAdapter);
    }

    private void nextTurn() {
        // 1. Award points to guessers
        List<Player> correctGuessers = guesserAdapter.getCorrectGuessers();
        for (Player p : correctGuessers) {
            // Find the original player object and add score
            for (Player original : players) {
                if (original.getName().equals(p.getName())) {
                    original.addScore(1);
                }
            }
        }

        // 2. Award points to the Spotlight player if anyone guessed correctly
        if (!correctGuessers.isEmpty()) {
            players.get(currentPlayerIndex).addScore(1);
        }

        // 3. Move to next player
        currentPlayerIndex++;
        if (currentPlayerIndex >= players.size()) {
            currentPlayerIndex = 0;
            showScoreboard();
        } else {
            loadTurn();
        }
    }

    private void showScoreboard() {
        StringBuilder sb = new StringBuilder("Round Over! Current Scores:\n");
        for (Player p : players) {
            sb.append(p.getName()).append(": ").append(p.getScore()).append("\n");
        }
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        loadTurn(); // Start next round for now
    }
}
