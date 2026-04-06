package com.spotlight;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AnswerChoiceAdapter extends RecyclerView.Adapter<AnswerChoiceAdapter.ChoiceViewHolder> {

    public interface OnChoiceActionListener {
        void onChoiceSelected(String choice);
        default void onMatchClicked(String choice) {}
        default void onDeleteClicked(String choice) {}
    }

    private List<String> choices;
    private OnChoiceActionListener listener;
    private int selectedPosition = -1;
    private boolean isReviewMode = false;

    public AnswerChoiceAdapter(List<String> choices, OnChoiceActionListener listener) {
        this.choices = choices;
        this.listener = listener;
    }

    public void setReviewMode(boolean reviewMode) {
        this.isReviewMode = reviewMode;
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
            
            holder.buttonMatch.setOnClickListener(v -> listener.onMatchClicked(choice));
            holder.buttonDelete.setOnClickListener(v -> listener.onDeleteClicked(choice));
            holder.itemView.setBackgroundColor(Color.WHITE);
        } else {
            holder.buttonMatch.setVisibility(View.GONE);
            holder.buttonDelete.setVisibility(View.GONE);
            
            if (selectedPosition == position) {
                holder.itemView.setBackgroundColor(Color.LTGRAY);
            } else {
                holder.itemView.setBackgroundColor(Color.WHITE);
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
