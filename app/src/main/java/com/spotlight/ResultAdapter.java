package com.spotlight;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.model.Player;

import java.util.List;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ResultViewHolder> {

    private List<Player> players;

    public ResultAdapter(List<Player> players) {
        this.players = players;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        Player player = players.get(position);
        holder.textViewName.setText(player.getName());
        holder.textViewScore.setText(String.valueOf(player.getScore()));
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        TextView textViewName;
        TextView textViewScore;

        public ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewName = itemView.findViewById(R.id.textViewResultPlayerName);
            textViewScore = itemView.findViewById(R.id.textViewResultScore);
        }
    }
}
