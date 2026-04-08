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
    private int selectedColor;
    private View[] colorViews;

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

        setupColorSelection();

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
                    Player newPlayer = new Player(name);
                    newPlayer.setAvatarColor(selectedColor);
                    players.add(newPlayer);
                    adapter.notifyItemInserted(players.size() - 1);
                    editTextPlayerName.setText("");
                    resetColorSelection();
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

    private void setupColorSelection() {
        colorViews = new View[]{
                findViewById(R.id.colorBlue),
                findViewById(R.id.colorGreen),
                findViewById(R.id.colorOrange),
                findViewById(R.id.colorPurple),
                findViewById(R.id.colorRed),
                findViewById(R.id.colorTeal)
        };

        for (View v : colorViews) {
            v.setOnClickListener(view -> {
                for (View other : colorViews) {
                    other.setScaleX(1.0f);
                    other.setScaleY(1.0f);
                    other.setAlpha(0.6f);
                }
                view.setScaleX(1.2f);
                view.setScaleY(1.2f);
                view.setAlpha(1.0f);

                int colorResId = 0;
                String tag = (String) view.getTag();
                if ("blue".equals(tag)) colorResId = R.color.avatar_blue;
                else if ("green".equals(tag)) colorResId = R.color.avatar_green;
                else if ("orange".equals(tag)) colorResId = R.color.avatar_orange;
                else if ("purple".equals(tag)) colorResId = R.color.avatar_purple;
                else if ("red".equals(tag)) colorResId = R.color.avatar_red;
                else if ("teal".equals(tag)) colorResId = R.color.avatar_teal;

                selectedColor = getColor(colorResId);
            });
        }
        resetColorSelection();
    }

    private void resetColorSelection() {
        for (View v : colorViews) {
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setAlpha(0.6f);
        }
        // Default to first color
        colorViews[0].performClick();
    }
}
