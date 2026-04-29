package com.example.core;

import com.example.api.DataModel.User;
import com.example.api.UserService;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserServiceImpl implements UserService {

    private final Map<String, User> store = ImmutableMap.of(
        "1", new User("1", "Alice", "alice@example.com"),
        "2", new User("2", "Bob", "bob@example.com"),
        "3", new User("3", "Charlie", "charlie@example.com")
    );

    @Override
    public Optional<User> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<User> findAll() {
        return ImmutableList.copyOf(store.values());
    }

    @Override
    public User save(User user) {
        return user;
    }
}
