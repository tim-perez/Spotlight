package com.spotlight.logic;

import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionRepository {
    private List<Question> allQuestions;
    private List<Question> currentPool;

    public QuestionRepository() {
        allQuestions = new ArrayList<>();
        loadQuestions();
        currentPool = new ArrayList<>(allQuestions);
        Collections.shuffle(currentPool);
    }

    private void loadQuestions() {
        // PERSONAL
        allQuestions.add(new Question("What is my favorite childhood memory?", "Personal"));
        allQuestions.add(new Question("What is my biggest pet peeve?", "Personal"));
        allQuestions.add(new Question("What is my secret talent?", "Personal"));
        allQuestions.add(new Question("What is my dream job?", "Personal"));
        allQuestions.add(new Question("What was my first ever job?", "Personal"));
        allQuestions.add(new Question("What is my middle name?", "Personal"));
        allQuestions.add(new Question("How many siblings do I have?", "Personal"));
        allQuestions.add(new Question("What is my favorite home-cooked meal?", "Personal"));

        // FUNNY / LIGHTHEARTED
        allQuestions.add(new Question("What is the most embarrassing thing I've ever done?", "Funny"));
        allQuestions.add(new Question("Which celebrity would I want to be best friends with?", "Funny"));
        allQuestions.add(new Question("What's a weird habit that I have?", "Funny"));
        allQuestions.add(new Question("If I were a vegetable, which one would I be?", "Funny"));
        allQuestions.add(new Question("What's the worst haircut I've ever had?", "Funny"));
        allQuestions.add(new Question("What's my go-to karaoke song?", "Funny"));
        allQuestions.add(new Question("Which fictional world would I want to live in?", "Funny"));

        // HYPOTHETICAL
        allQuestions.add(new Question("If I could travel anywhere, where would I go?", "Hypothetical"));
        allQuestions.add(new Question("If I won the lottery, what is the first thing I would buy?", "Hypothetical"));
        allQuestions.add(new Question("If I could have any superpower, what would it be?", "Hypothetical"));
        allQuestions.add(new Question("If I could meet anyone from history, who would it be?", "Hypothetical"));
        allQuestions.add(new Question("If I could only eat one food for the rest of my life, what would it be?", "Hypothetical"));
        allQuestions.add(new Question("If I could go back in time, which era would I visit?", "Hypothetical"));

        // DEEP
        allQuestions.add(new Question("What is my biggest fear?", "Deep"));
        allQuestions.add(new Question("What am I most proud of in my life?", "Deep"));
        allQuestions.add(new Question("What is a life lesson I've learned the hard way?", "Deep"));
        allQuestions.add(new Question("Who has been the most influential person in my life?", "Deep"));
        allQuestions.add(new Question("What's one thing I want to be remembered for?", "Deep"));
    }

    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (Question q : allQuestions) {
            if (!categories.contains(q.getCategory())) {
                categories.add(q.getCategory());
            }
        }
        return categories;
    }

    public void filterByCategory(String category) {
        if (category.equals("All")) {
            currentPool = new ArrayList<>(allQuestions);
        } else {
            currentPool = new ArrayList<>();
            for (Question q : allQuestions) {
                if (q.getCategory().equals(category)) {
                    currentPool.add(q);
                }
            }
        }
        Collections.shuffle(currentPool);
    }

    public Question getRandomQuestion() {
        if (currentPool.isEmpty()) {
            loadQuestions(); // Reset if empty
            currentPool = new ArrayList<>(allQuestions);
            Collections.shuffle(currentPool);
        }
        return currentPool.remove(0);
    }
}
