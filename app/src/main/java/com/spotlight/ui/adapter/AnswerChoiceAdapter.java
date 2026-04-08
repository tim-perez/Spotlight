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

import com.spotlight.databinding.ItemChoiceBinding;

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

    public void setListener(OnChoiceActionListener listener) {
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
        ItemChoiceBinding binding = ItemChoiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ChoiceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceViewHolder holder, int position) {
        String choice = choices.get(position);
        holder.binding.textViewChoice.setText(choice);
        
        if (isReviewMode) {
            holder.binding.buttonMatch.setVisibility(View.VISIBLE);
            holder.binding.buttonDelete.setVisibility(View.VISIBLE);
            holder.binding.getRoot().setOnClickListener(null);
            
            if (matchedPositions.contains(position)) {
                holder.binding.getRoot().setBackgroundColor(Color.parseColor("#2E7D32")); // Darker Green
                holder.binding.buttonMatch.setImageResource(android.R.drawable.checkbox_on_background);
            } else {
                holder.binding.getRoot().setBackgroundColor(Color.parseColor("#1E1E1E")); // Dark Surface Color
                holder.binding.buttonMatch.setImageResource(android.R.drawable.checkbox_off_background);
            }

            holder.binding.buttonMatch.setOnClickListener(v -> listener.onMatchClicked(choice, holder.getAdapterPosition()));
            holder.binding.buttonDelete.setOnClickListener(v -> listener.onDeleteClicked(holder.getAdapterPosition()));
        } else {
            holder.binding.buttonMatch.setVisibility(View.GONE);
            holder.binding.buttonDelete.setVisibility(View.GONE);
            
            if (selectedPosition == position) {
                holder.binding.getRoot().setBackgroundColor(Color.parseColor("#333333")); // Selected Dark Gray
            } else {
                holder.binding.getRoot().setBackgroundColor(Color.TRANSPARENT);
            }

            holder.binding.getRoot().setOnClickListener(v -> {
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
        final ItemChoiceBinding binding;

        public ChoiceViewHolder(@NonNull ItemChoiceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
