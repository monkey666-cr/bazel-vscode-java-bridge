package com.example.core;

import com.example.api.DataModel.User;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.util.List;
import java.util.stream.Collectors;

public class DataProcessor {

    public String serializeUser(User user) {
        return Joiner.on("|").join(user.getId(), user.getName(), user.getEmail());
    }

    public User deserializeUser(String data) {
        List<String> parts = Splitter.on("|").splitToList(data);
        return new User(parts.get(0), parts.get(1), parts.get(2));
    }

    public List<String> extractEmails(List<User> users) {
        return users.stream()
            .map(User::getEmail)
            .collect(Collectors.toList());
    }
}
