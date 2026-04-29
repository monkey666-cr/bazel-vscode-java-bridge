package com.example.app;

import com.example.service.UserService;
import com.example.utils.StringUtils;

public class Application {
    public static void main(String[] args) {
        UserService userService = new UserService();

        System.out.println("User 1: " + userService.getUserName("1"));
        System.out.println("User 99: " + userService.getUserName("99"));
        System.out.println("All users: " + userService.listAllUsers());

        String input = "hello, world, bazel";
        System.out.println("Split: " + StringUtils.splitByComma(input));
    }
}
