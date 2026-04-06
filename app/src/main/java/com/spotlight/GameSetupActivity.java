package com.spotlight;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.List;

public class GameSetupActivity extends AppCompatActivity {

    private List<Player> players = new ArrayList<>();
    private PlayerAdapter adapter;
    private EditText editTextPlayerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_setup);

        editTextPlayerName = findViewById(R.id.editTextPlayerName);
        RecyclerView recyclerViewPlayers = findViewById(R.id.recyclerViewPlayers);
        Button buttonAddPlayer = findViewById(R.id.buttonAddPlayer);
        Button buttonStartGame = findViewById(R.id.buttonStartGame);
        View buttonBack = findViewById(R.id.buttonBack);

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
                    Toast.makeText(GameSetupActivity.this, "Enter a name", Toast.LENGTH_SHORT).show();
                }
            }
        });

        buttonStartGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (players.size() >= 3) {
                    Intent intent = new Intent(GameSetupActivity.this, GameActivity.class);
                    intent.putExtra("players", (ArrayList<Player>) players);
                    startActivity(intent);
                } else {
                    Toast.makeText(GameSetupActivity.this, "Need at least 3 players", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
