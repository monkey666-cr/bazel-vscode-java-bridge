package com.example.server;

import com.example.api.DataModel.User;
import com.example.api.UserService;
import com.example.core.DataProcessor;
import com.example.core.UserServiceImpl;

public class ServerMain {
    public static void main(String[] args) {
        UserService userService = new UserServiceImpl();
        DataProcessor processor = new DataProcessor();

        System.out.println("=== Multi-Module Server ===");

        for (User user : userService.findAll()) {
            String serialized = processor.serializeUser(user);
            System.out.println("Serialized: " + serialized);

            User deserialized = processor.deserializeUser(serialized);
            System.out.println("Deserialized: " + deserialized);
        }

        System.out.println("Emails: " + processor.extractEmails(userService.findAll()));
    }
}
