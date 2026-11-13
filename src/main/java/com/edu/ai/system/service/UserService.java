
package com.edu.ai.system.service;

import com.edu.ai.system.entity.User;
import java.util.List;

public interface UserService {

    User getByUsername(String username);

    User getUserById(Long id);

    List<User> getUserList();

    boolean addUser(User user);

    boolean updateUser(User user);

    boolean deleteUser(Long id);

    boolean updateUserStatus(Long id, Integer status);
}

