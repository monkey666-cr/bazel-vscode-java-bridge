package com.example.service;

import com.example.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import java.util.Map;

public class UserService {

    private final Map<String, String> users = ImmutableMap.of(
        "1", "Alice",
        "2", "Bob",
        "3", "Charlie"
    );

    public String getUserName(String id) {
        String name = users.get(id);
        if (StringUtils.isNullOrEmpty(name)) {
            return "Unknown User";
        }
        return name;
    }

    public String listAllUsers() {
        return StringUtils.joinWithComma(ImmutableList.copyOf(users.values()));
    }
}
