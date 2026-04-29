package com.example.integration;

import static org.junit.Assert.assertEquals;

import com.example.api.DataModel.User;
import com.example.core.DataProcessor;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DataProcessorTest {

    private final DataProcessor processor = new DataProcessor();

    @Test
    public void testSerializeUser() {
        User user = new User("1", "Alice", "alice@example.com");
        String result = processor.serializeUser(user);
        assertEquals("1|Alice|alice@example.com", result);
    }

    @Test
    public void testDeserializeUser() {
        User result = processor.deserializeUser("2|Bob|bob@example.com");
        assertEquals("2", result.getId());
        assertEquals("Bob", result.getName());
        assertEquals("bob@example.com", result.getEmail());
    }

    @Test
    public void testExtractEmails() {
        List<User> users = Arrays.asList(
            new User("1", "A", "a@test.com"),
            new User("2", "B", "b@test.com")
        );
        List<String> emails = processor.extractEmails(users);
        assertEquals(Arrays.asList("a@test.com", "b@test.com"), emails);
    }
}
