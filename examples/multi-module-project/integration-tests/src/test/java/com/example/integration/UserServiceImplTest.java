package com.example.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.api.DataModel.User;
import com.example.api.UserService;
import com.example.core.UserServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class UserServiceImplTest {

    private final UserService service = new UserServiceImpl();

    @Test
    public void testFindById() {
        Optional<User> user = service.findById("1");
        assertTrue(user.isPresent());
        assertEquals("Alice", user.get().getName());
    }

    @Test
    public void testFindByIdNotFound() {
        Optional<User> user = service.findById("999");
        assertFalse(user.isPresent());
    }

    @Test
    public void testFindAll() {
        List<User> users = service.findAll();
        assertEquals(3, users.size());
    }

    @Test
    public void testSave() {
        User newUser = new User("4", "Dave", "dave@example.com");
        User saved = service.save(newUser);
        assertEquals("Dave", saved.getName());
    }
}
