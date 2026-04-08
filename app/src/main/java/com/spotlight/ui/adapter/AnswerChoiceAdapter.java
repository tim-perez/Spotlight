package com.spotlight.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spotlight.R;

import java.util.List;

public class AnswerChoiceAdapter extends RecyclerView.Adapter<AnswerChoiceAdapter.ChoiceViewHolder> {

    public interface OnChoiceActionListener {
        void onChoiceSelected(String choice);
        default void onMatchClicked(String choice, int position) {}
        default void onDeleteClicked(int position) {}
    }

    private List<String> choices;
    private OnChoiceActionListener listener;
    private int selectedPosition = -1;
    private boolean isReviewMode = false;
    private java.util.Set<Integer> matchedPositions = new java.util.HashSet<>();

    public AnswerChoiceAdapter(List<String> choices, OnChoiceActionListener listener) {
        this.choices = choices;
        this.listener = listener;
    }

    public void setReviewMode(boolean reviewMode) {
        this.isReviewMode = reviewMode;
    }

    public void setMatchedPositions(java.util.Set<Integer> matchedPositions) {
        this.matchedPositions = matchedPositions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_choice, parent, false);
        return new ChoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceViewHolder holder, int position) {
        String choice = choices.get(position);
        holder.textViewChoice.setText(choice);
        
        if (isReviewMode) {
            holder.buttonMatch.setVisibility(View.VISIBLE);
            holder.buttonDelete.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(null);
            
            if (matchedPositions.contains(position)) {
                holder.itemView.setBackgroundColor(Color.parseColor("#2E7D32")); // Darker Green
                holder.buttonMatch.setImageResource(android.R.drawable.checkbox_on_background);
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#1E1E1E")); // Dark Surface Color
                holder.buttonMatch.setImageResource(android.R.drawable.checkbox_off_background);
            }

            holder.buttonMatch.setOnClickListener(v -> listener.onMatchClicked(choice, holder.getAdapterPosition()));
            holder.buttonDelete.setOnClickListener(v -> listener.onDeleteClicked(holder.getAdapterPosition()));
        } else {
            holder.buttonMatch.setVisibility(View.GONE);
            holder.buttonDelete.setVisibility(View.GONE);
            
            if (selectedPosition == position) {
                holder.itemView.setBackgroundColor(Color.parseColor("#333333")); // Selected Dark Gray
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            holder.itemView.setOnClickListener(v -> {
                int previousSelected = selectedPosition;
                selectedPosition = holder.getAdapterPosition();
                notifyItemChanged(previousSelected);
                notifyItemChanged(selectedPosition);
                listener.onChoiceSelected(choice);
            });
        }
    }

    @Override
    public int getItemCount() {
        return choices.size();
    }

    static class ChoiceViewHolder extends RecyclerView.ViewHolder {
        TextView textViewChoice;
        ImageButton buttonMatch;
        ImageButton buttonDelete;

        public ChoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewChoice = itemView.findViewById(R.id.textViewChoice);
            buttonMatch = itemView.findViewById(R.id.buttonMatch);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
