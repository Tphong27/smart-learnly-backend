package com.smartlearnly.backend;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GeneratePasswordHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String rawPassword = "superonepiece123";
        String hash = encoder.encode(rawPassword);

        System.out.println(hash);
    }
}
