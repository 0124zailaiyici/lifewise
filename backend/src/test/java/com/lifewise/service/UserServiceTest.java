package com.lifewise.service;

import com.lifewise.dto.LoginRequest;
import com.lifewise.dto.RegisterRequest;
import com.lifewise.dto.UserResponse;
import com.lifewise.entity.User;
import com.lifewise.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPhone("13800138001");
        request.setPassword("password123");

        UserResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals("13800138001", response.getPhone());
        assertNotNull(response.getId());
    }

    @Test
    void shouldRejectDuplicatePhone() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user1");
        request.setPhone("13800138002");
        request.setPassword("pass123");
        userService.register(request);

        RegisterRequest request2 = new RegisterRequest();
        request2.setUsername("user2");
        request2.setPhone("13800138002");
        request2.setPassword("pass456");

        assertThrows(RuntimeException.class, () -> userService.register(request2));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        RegisterRequest r1 = new RegisterRequest();
        r1.setUsername("sameuser");
        r1.setPhone("13800138003");
        r1.setPassword("pass123");
        userService.register(r1);

        RegisterRequest r2 = new RegisterRequest();
        r2.setUsername("sameuser");
        r2.setPhone("13800138004");
        r2.setPassword("pass456");

        assertThrows(RuntimeException.class, () -> userService.register(r2));
    }

    @Test
    void shouldLoginWithCorrectPassword() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("logintest");
        reg.setPhone("13800138005");
        reg.setPassword("mypassword");
        userService.register(reg);

        LoginRequest login = new LoginRequest();
        login.setPhone("13800138005");
        login.setPassword("mypassword");

        var response = userService.login(login);

        assertNotNull(response);
        assertEquals("logintest", response.getUsername());
        assertNotNull(response.getToken());
    }

    @Test
    void shouldRejectWrongPassword() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("wrongpass");
        reg.setPhone("13800138006");
        reg.setPassword("correctpass");
        userService.register(reg);

        LoginRequest login = new LoginRequest();
        login.setPhone("13800138006");
        login.setPassword("wrongpass");

        assertThrows(RuntimeException.class, () -> userService.login(login));
    }

    @Test
    void shouldRejectNonExistentUser() {
        LoginRequest login = new LoginRequest();
        login.setPhone("13900000000");
        login.setPassword("anypass");

        assertThrows(RuntimeException.class, () -> userService.login(login));
    }

    @Test
    void shouldGetUserById() {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("getbyid");
        reg.setPhone("13800138007");
        reg.setPassword("pass123");
        UserResponse created = userService.register(reg);

        UserResponse found = userService.getUserById(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("getbyid", found.getUsername());
    }
}
