package com.example.greeter;

public class Greeter {

    private final String name;

    public Greeter(String name) {
        this.name = name;
    }

    public String greet() {
        return "Hello, " + name + "!";
    }

    public String formalGreet() {
        return "Dear " + name + ", it is a pleasure to meet you.";
    }
}
