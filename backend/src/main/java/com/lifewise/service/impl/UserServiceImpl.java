package com.lifewise.service.impl;

import com.lifewise.config.JwtUtil;
import com.lifewise.dto.*;
import com.lifewise.entity.User;
import com.lifewise.repository.UserRepository;
import com.lifewise.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 内存验证码存储 key=phone, value=code
    private final Map<String, String> verificationCodes = new HashMap<>();

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("手机号已被注册");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已被使用");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        return toResponse(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
            .orElseThrow(() -> new RuntimeException("用户不存在或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户不存在或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getPhone());

        LoginResponse resp = new LoginResponse();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setPhone(user.getPhone());
        resp.setAvatar(user.getAvatar());
        resp.setToken(token);
        return resp;
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toResponse(user);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        String phone = request.getPhone();
        if (!userRepository.existsByPhone(phone)) {
            // 不暴露手机号是否存在，统一返回成功
            log.info("密码重置: 手机号 {} 未注册，但仍生成验证码（调试模式）", phone);
        }

        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(999999));
        verificationCodes.put(phone, code);

        log.info("密码重置验证码 [{}] -> 手机号: {}", code, phone);
        // 实际项目中这里调用短信服务发送验证码
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String phone = request.getPhone();
        String code = verificationCodes.get(phone);

        if (code == null || !code.equals(request.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User user = userRepository.findByPhone(phone)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        verificationCodes.remove(phone);
    }

    private UserResponse toResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setPhone(user.getPhone());
        r.setAvatar(user.getAvatar());
        return r;
    }
}
