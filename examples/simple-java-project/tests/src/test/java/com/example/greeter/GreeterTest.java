package com.example.greeter;

public class GreeterTest {

    public static void main(String[] args) {
        new GreeterTest().testGreet();
        new GreeterTest().testFormalGreet();
        System.out.println("All tests passed!");
    }

    public void testGreet() {
        Greeter greeter = new Greeter("Bazel");
        if (!greeter.greet().equals("Hello, Bazel!")) {
            throw new RuntimeException("testGreet failed");
        }
    }

    public void testFormalGreet() {
        Greeter greeter = new Greeter("World");
        if (!greeter.formalGreet().equals("Dear World, it is a pleasure to meet you.")) {
            throw new RuntimeException("testFormalGreet failed");
        }
    }
}
