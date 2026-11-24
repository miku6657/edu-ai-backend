package com.edu.ai.system.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.system.dto.UserDTO;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.service.UserService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(
            UserService userService,
            PasswordEncoder passwordEncoder) {

        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public Result<List<UserDTO>> list() {

        List<UserDTO> users = userService.getUserList()
                .stream()
                .map(this::toDTO)
                .toList();

        return Result.success(users);
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getById(@PathVariable Long id) {

        return Result.success(
                toDTO(requireUser(id))
        );
    }

    @PostMapping
    public Result<String> add(@RequestBody UserDTO dto) {

        if (dto.getUsername() == null
                || dto.getUsername().isBlank()
                || dto.getPassword() == null
                || dto.getPassword().isBlank()) {

            throw new BusinessException(
                    400,
                    "用户名和密码不能为空"
            );
        }

        String role = normalizeRole(dto.getRole());

        User user = new User();

        user.setUsername(dto.getUsername().trim());
        user.setPassword(
                passwordEncoder.encode(dto.getPassword())
        );
        user.setNickname(dto.getNickname());
        user.setRole(role);

        userService.addUser(user);

        return Result.success("用户创建成功");
    }

    @PutMapping("/{id}")
    public Result<String> update(
            @PathVariable Long id,
            @RequestBody UserDTO dto) {

        requireUser(id);

        User user = new User();
        user.setId(id);
        user.setNickname(dto.getNickname());

        if (dto.getRole() != null) {
            user.setRole(normalizeRole(dto.getRole()));
        }

        userService.updateUser(user);

        return Result.success("用户修改成功");
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {

        requireUser(id);

        userService.deleteUser(id);

        return Result.success("用户删除成功");
    }

    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {

        requireUser(id);

        Integer status = body.get("status");

        userService.updateUserStatus(id, status);

        return Result.success("用户状态修改成功");
    }

    private User requireUser(Long id) {

        User user = userService.getUserById(id);

        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return user;
    }

    private String normalizeRole(String role) {

        if (!UserRole.isValid(role)) {
            throw new BusinessException(
                    400,
                    "用户角色只能是 ADMIN、TEACHER 或 STUDENT"
            );
        }

        return role.trim().toUpperCase(Locale.ROOT);
    }

    private UserDTO toDTO(User user) {

        UserDTO dto = new UserDTO();

        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());

        return dto;
    }
}
