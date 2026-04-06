package com.spotlight.logic;

import com.spotlight.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestionRepository {
    private List<Question> questions;

    public QuestionRepository() {
        questions = new ArrayList<>();
        loadQuestions();
        Collections.shuffle(questions);
    }

    private void loadQuestions() {
        questions.add(new Question("What is my favorite childhood memory?"));
        questions.add(new Question("If I could travel anywhere, where would I go?"));
        questions.add(new Question("What is my biggest pet peeve?"));
        questions.add(new Question("What is the most embarrassing thing I've ever done?"));
        questions.add(new Question("What is my secret talent?"));
        questions.add(new Question("If I won the lottery, what is the first thing I would buy?"));
        questions.add(new Question("What is my dream job?"));
        questions.add(new Question("Which celebrity would I want to be best friends with?"));
    }

    public Question getRandomQuestion() {
        if (questions.isEmpty()) {
            loadQuestions();
            Collections.shuffle(questions);
        }
        return questions.remove(0);
    }
}
