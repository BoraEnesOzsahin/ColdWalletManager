package com.ayrotek.coldwalletmanagerservice;
import org.springframework.web.client.RestTemplate;
import java.lang.reflect.Constructor;
public class Test {
    public static void main(String[] args) {
        for (Constructor<?> c : RestTemplate.class.getConstructors()) {
            System.out.println(c);
        }
    }
}