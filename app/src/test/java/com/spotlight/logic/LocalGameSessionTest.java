package com.spotlight.logic;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.spotlight.model.Player;
import com.spotlight.model.Question;
import com.spotlight.model.RoomStatus;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class LocalGameSessionTest {

    // This rule forces LiveData to execute instantly on the current test thread
    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private LocalGameSession session;
    private List<Player> testPlayers;

    @Before
    public void setup() {
        // 1. Create a "Mock" QuestionRepository so we don't need a real questions.json file
        QuestionRepository mockRepo = Mockito.mock(QuestionRepository.class);
        when(mockRepo.getRandomQuestion()).thenReturn(new Question("Test Question?", "Test"));

        // 2. Inject the mock into our session
        session = new LocalGameSession(mockRepo);

        // 3. Set up dummy players
        Player p0 = new Player("0", "Spotlight");
        Player p1 = new Player("1", "GuesserOne");
        Player p2 = new Player("2", "GuesserTwo");
        testPlayers = Arrays.asList(p0, p1, p2);

        // 4. Initialize the game (Player 0 becomes the Spotlight)
        session.init("ROOM", "0", "0", testPlayers, "All");
    }

    @Test
    public void testCalculateScores_ReviewMatchAwardsFourPoints() {
        // --- ARRANGE ---
        session.submitAnswer("Pizza");   // P0 (Spotlight)
        session.submitAnswer("Tacos");   // P1
        session.submitAnswer("Pizza");   // P2

        // --- ACT ---
        session.toggleMatch("Pizza");    // Spotlight flags "Pizza" as a match
        session.calculateScores(null);   // Trigger scoring

        // --- ASSERT ---
        List<Player> updatedPlayers = session.getPlayers().getValue();
        assertEquals("Spotlight should get 0 points", 0, updatedPlayers.get(0).getScore());
        assertEquals("P1 should get 0 points", 0, updatedPlayers.get(1).getScore());
        assertEquals("P2 should get 4 points for matching", 4, updatedPlayers.get(2).getScore());
    }

    @Test
    public void testCalculateScores_VotingPhaseAwardsCorrectPoints() {
        // --- ARRANGE ---
        session.submitAnswer("Blue");   // P0 (Spotlight)
        session.submitAnswer("Red");    // P1
        session.submitAnswer("Green");  // P2

        // Jump to voting phase
        session.updateStatus(RoomStatus.VOTING);
        // --- ACT ---
        // P1 guesses "Blue" (Correctly guessed the Spotlight's answer)
        session.submitVote("Blue");

        // P2 guesses "Red" (Incorrectly guessed P1's answer instead of Spotlight's)
        session.submitVote("Red");

        // --- ASSERT ---
        // P0 (Spotlight): +1 point because P1 guessed correctly
        // P1: +2 points (correct guess) + 1 point (tricked P2) = 3 total points
        // P2: 0 points (guessed wrong, tricked nobody)

        List<Player> updatedPlayers = session.getPlayers().getValue();
        assertEquals("Spotlight gets 1 point", 1, updatedPlayers.get(0).getScore());
        assertEquals("P1 gets 3 points", 3, updatedPlayers.get(1).getScore());
        assertEquals("P2 gets 0 points", 0, updatedPlayers.get(2).getScore());
    }
}