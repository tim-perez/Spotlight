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
import com.spotlight.logic.QuestionRepository;
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

        viewModel = new ViewModelProvider(this).get(CreateRoomViewModel.class);

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

        QuestionRepository questionRepository = new QuestionRepository(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, questionRepository.getCategories());
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCategory.setAdapter(categoryAdapter);

        binding.buttonBack.setOnClickListener(v -> finish());

        adapter = new PlayerAdapter(new ArrayList<>());
        binding.recyclerViewRoomPlayers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewRoomPlayers.setAdapter(adapter);

        binding.buttonGenerateRoom.setOnClickListener(v -> {
            String name = binding.editTextHostName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.hint_enter_your_name, Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.createRoom(name, selectedColor);
        });

        binding.buttonStartMultiplayer.setOnClickListener(v -> {
            GameRoom room = viewModel.getRoomData().getValue();
            if (room != null && room.getPlayers().size() < 3) {
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
                binding.textViewRoomCodeDisplay.setText(getString(R.string.room_code_format, viewModel.getRoomCode()));
                binding.layoutRoomInfo.setVisibility(View.VISIBLE);
                binding.buttonStartMultiplayer.setVisibility(View.VISIBLE);
                binding.buttonGenerateRoom.setVisibility(View.GONE);
                binding.layoutColorsHost.setVisibility(View.GONE);
                binding.editTextHostName.setEnabled(false);
            }
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getRoomData().observe(this, room -> {
            if (room != null && room.getPlayers() != null) {
                List<Player> playersList = new ArrayList<>(room.getPlayers().values());
                adapter.setPlayers(playersList);
                adapter.setHostId(room.getHostId());
                adapter.notifyDataSetChanged();

                if ("IN_PROGRESS".equals(room.getStatus())) {
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
    }
}
