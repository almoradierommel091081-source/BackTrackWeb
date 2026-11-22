package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.PasswordChangeForm;
import org.example.backtrackweb.dto.UserForm;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account management: creating employees, editing them, deactivating them and
 * changing passwords. Passwords are always hashed with BCrypt.
 */
@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return repository.findAllByOrderByRoleAscFullNameAsc();
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public long countOwners() {
        return repository.countByRoleAndActiveTrue(Role.OWNER);
    }

    @Transactional
    public User create(UserForm form) {
        String username = form.getUsername().trim();
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("That username is already taken.");
        }
        if (form.getPassword() == null || form.getPassword().length() < 8) {
            throw new BusinessException("Password must be at least 8 characters.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two passwords do not match.");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        applyEditableFields(user, form);
        return repository.save(user);
    }

    @Transactional
    public User update(Long id, UserForm form, User actingUser) {
        User user = get(id);
        String username = form.getUsername().trim();
        repository.findByUsernameIgnoreCase(username)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new BusinessException("That username is already taken.");
                });

        boolean losesOwnerAccess = user.isOwner() && (form.getRole() != Role.OWNER || !form.isActive());
        if (losesOwnerAccess && repository.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
            throw new BusinessException("You cannot remove the last active owner account.");
        }
        if (user.getId().equals(actingUser.getId()) && !form.isActive()) {
            throw new BusinessException("You cannot deactivate your own account.");
        }

        user.setUsername(username);
        applyEditableFields(user, form);

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            if (form.getPassword().length() < 8) {
                throw new BusinessException("Password must be at least 8 characters.");
            }
            if (!form.getPassword().equals(form.getConfirmPassword())) {
                throw new BusinessException("The two passwords do not match.");
            }
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }
        return repository.save(user);
    }

    @Transactional
    public void setActive(Long id, boolean active, User actingUser) {
        User user = get(id);
        if (user.getId().equals(actingUser.getId()) && !active) {
            throw new BusinessException("You cannot deactivate your own account.");
        }
        if (!active && user.isOwner() && repository.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
            throw new BusinessException("You cannot deactivate the last active owner account.");
        }
        user.setActive(active);
        repository.save(user);
    }

    @Transactional
    public void changePassword(User user, PasswordChangeForm form) {
        User current = get(user.getId());
        if (!passwordEncoder.matches(form.getCurrentPassword(), current.getPasswordHash())) {
            throw new BusinessException("Your current password is not correct.");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("The two new passwords do not match.");
        }
        if (form.getNewPassword().equals(form.getCurrentPassword())) {
            throw new BusinessException("The new password must be different from the current one.");
        }
        current.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        repository.save(current);
    }

    @Transactional
    public User resetPassword(Long id, String newPassword) {
        User user = get(id);
        if (newPassword == null || newPassword.length() < 8) {
            throw new BusinessException("Password must be at least 8 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return repository.save(user);
    }

    private void applyEditableFields(User user, UserForm form) {
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail());
        user.setRole(form.getRole());
        user.setActive(form.isActive());
        // Only owners may manage customers; the flag is meaningless for an owner.
        user.setCanManageCustomers(form.getRole() == Role.EMPLOYEE && form.isCanManageCustomers());
    }
}
