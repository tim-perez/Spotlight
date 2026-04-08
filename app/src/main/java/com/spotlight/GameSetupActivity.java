package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.logic.QuestionRepository;
import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.List;

public class GameSetupActivity extends AppCompatActivity {

    private List<Player> players = new ArrayList<>();
    private PlayerAdapter adapter;
    private EditText editTextPlayerName;
    private Spinner spinnerCategory;
    private QuestionRepository questionRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_setup);

        editTextPlayerName = findViewById(R.id.editTextPlayerName);
        RecyclerView recyclerViewPlayers = findViewById(R.id.recyclerViewPlayers);
        Button buttonAddPlayer = findViewById(R.id.buttonAddPlayer);
        Button buttonStartGame = findViewById(R.id.buttonStartGame);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        View buttonBack = findViewById(R.id.buttonBack);

        questionRepository = new QuestionRepository(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, questionRepository.getCategories());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(players);
        recyclerViewPlayers.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPlayers.setAdapter(adapter);

        buttonAddPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = editTextPlayerName.getText().toString().trim();
                if (!name.isEmpty()) {
                    players.add(new Player(name));
                    adapter.notifyItemInserted(players.size() - 1);
                    editTextPlayerName.setText("");
                } else {
                    Toast.makeText(GameSetupActivity.this, R.string.error_enter_name, Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (players.size() >= 3) {
                    Intent intent = new Intent(GameSetupActivity.this, GameActivity.class);
                    intent.putExtra("players", (ArrayList<Player>) players);
                    intent.putExtra("isMultiplayer", false);
                    intent.putExtra("category", spinnerCategory.getSelectedItem().toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(GameSetupActivity.this, R.string.error_min_players, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
