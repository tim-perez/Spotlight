package com.spotlight.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.R;
import com.spotlight.model.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuesserAdapter extends RecyclerView.Adapter<GuesserAdapter.GuesserViewHolder> {

    private List<Player> players;
    private Set<Integer> correctGuessersPositions = new HashSet<>();

    public GuesserAdapter(List<Player> players) {
        this.players = players;
    }

    @NonNull
    @Override
    public GuesserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guesser, parent, false);
        return new GuesserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GuesserViewHolder holder, int position) {
        Player player = players.get(position);
        holder.textViewGuesserName.setText(player.getName());
        holder.checkBoxGuessedCorrectly.setOnCheckedChangeListener(null);
        holder.checkBoxGuessedCorrectly.setChecked(correctGuessersPositions.contains(position));
        holder.checkBoxGuessedCorrectly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                correctGuessersPositions.add(position);
            } else {
                correctGuessersPositions.remove(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    public List<Player> getCorrectGuessers() {
        List<Player> correctGuessers = new ArrayList<>();
        for (int i : correctGuessersPositions) {
            correctGuessers.add(players.get(i));
        }
        return correctGuessers;
    }

    public void clearGuesses() {
        correctGuessersPositions.clear();
        notifyDataSetChanged();
    }

    static class GuesserViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBoxGuessedCorrectly;
        TextView textViewGuesserName;

        public GuesserViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBoxGuessedCorrectly = itemView.findViewById(R.id.checkBoxGuessedCorrectly);
            textViewGuesserName = itemView.findViewById(R.id.textViewGuesserName);
        }
    }
}
