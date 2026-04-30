package com.spotlight.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spotlight.R;
import com.spotlight.databinding.ActivityCreateRoomBinding;
import com.spotlight.logic.CreateRoomViewModel;
import com.spotlight.logic.ViewModelFactory;
import com.spotlight.model.GameRoom;
import com.spotlight.model.Player;
import com.spotlight.ui.adapter.PlayerAdapter;
import com.spotlight.util.AvatarUtils;

import java.util.ArrayList;
import java.util.List;

public class CreateRoomActivity extends AppCompatActivity {

    private ActivityCreateRoomBinding binding;
    private CreateRoomViewModel viewModel;
    private PlayerAdapter adapter;
    private int selectedColor;
    private View[] colorViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewModelFactory factory = new ViewModelFactory(this);
        viewModel = new ViewModelProvider(this, factory).get(CreateRoomViewModel.class);

        initViews();
        setupObservers();
    }

    private void initViews() {
        colorViews = new View[]{
                binding.colorBlue,
                binding.colorGreen,
                binding.colorOrange,
                binding.colorPurple,
                binding.colorRed,
                binding.colorTeal,
                binding.colorPink
        };
        AvatarUtils.setupColorSelection(this, colorViews, color -> selectedColor = color);
        AvatarUtils.resetColorSelection(colorViews);

        binding.buttonBack.setOnClickListener(v -> {
            viewModel.leaveRoom();
            finish();
        });

        adapter = new PlayerAdapter(new ArrayList<>(), null);
        binding.recyclerViewRoomPlayers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewRoomPlayers.setAdapter(adapter);

        binding.buttonGenerateRoom.setOnClickListener(v -> {
            String name = binding.editTextHostName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.hint_enter_your_name, Toast.LENGTH_SHORT).show();
                return;
            }

            if (com.spotlight.util.ProfanityFilter.containsProfanity(name)) {
                Toast.makeText(this, "Please keep names appropriate!", Toast.LENGTH_SHORT).show();
                return; // Abort creation!
            }

            // Visual feedback that the button was clicked
            binding.buttonGenerateRoom.setText("Creating...");
            binding.buttonGenerateRoom.setEnabled(false);

            viewModel.createRoom(name, selectedColor);
        });

        binding.buttonStartMultiplayer.setOnClickListener(v -> {
            GameRoom room = viewModel.getRoomData().getValue();

            // THE FIX: Lowered from < 3 to < 1 so you can test the game by yourself!
            if (room != null && room.getPlayers().size() < 1) {
                Toast.makeText(this, R.string.error_wait_players, Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedCategory = binding.spinnerCategory.getSelectedItem().toString();
            viewModel.startGame(selectedCategory);
        });
    }

    private void setupObservers() {
        viewModel.getIsRoomCreated().observe(this, isCreated -> {
            if (isCreated) {
                String code = viewModel.getRoomCode();
                binding.textViewRoomCodeDisplay.setText("Room Code: " + code + "\n(Tap to Share Invite Link)");
                binding.layoutRoomInfo.setVisibility(View.VISIBLE);
                binding.buttonStartMultiplayer.setVisibility(View.VISIBLE);
                binding.buttonGenerateRoom.setVisibility(View.GONE);
                binding.layoutColorsHost.setVisibility(View.GONE);
                binding.editTextHostName.setEnabled(false);

                // THE FIX: Allow the host to tap the code to send a Deep Link
                binding.textViewRoomCodeDisplay.setOnClickListener(v -> {
                    String inviteLink = "spotlight://join?code=" + code;
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT,
                            "Join my Spotlight game! Tap here: " + inviteLink + "\n(Or enter code: " + code + ")");
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, "Share Invite Link"));
                });
            }
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                binding.buttonGenerateRoom.setText("Create Room");
                binding.buttonGenerateRoom.setEnabled(true);
            }
        });

        viewModel.getRoomData().observe(this, room -> {
            if (room != null && room.getPlayers() != null) {
                List<Player> playersList = new ArrayList<>(room.getPlayers().values());
                adapter.setPlayers(playersList);
                adapter.setHostId(room.getHostId());
                adapter.notifyDataSetChanged();

                // THE FIX: Check against a raw String instead of getStatusEnum()
                if (!"WAITING".equals(room.getStatus())) {
                    Intent intent = new Intent(this, GameActivity.class);
                    intent.putExtra("players", new ArrayList<>(playersList));
                    intent.putExtra("isMultiplayer", true);
                    intent.putExtra("roomCode", viewModel.getRoomCode());
                    intent.putExtra("playerId", viewModel.getPlayerId());
                    intent.putExtra("hostId", room.getHostId());
                    intent.putExtra("category", room.getCategory());
                    startActivity(intent);
                    finish();
                }
            }
        });

        viewModel.getCategoriesLiveData().observe(this, categories -> {
            if (categories != null && !categories.isEmpty()) {
                ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
                categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.spinnerCategory.setAdapter(categoryAdapter);
            }
        });
    }

    @Override
    public void onBackPressed() {
        viewModel.leaveRoom();
        super.onBackPressed();
    }
}