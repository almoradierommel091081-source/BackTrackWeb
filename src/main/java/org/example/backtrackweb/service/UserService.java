package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.PasswordChangeForm;
import org.example.backtrackweb.dto.UserForm;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Account management: creating employees, editing them, deactivating them and
 * changing passwords. Passwords are always hashed with BCrypt.
 *
 * <p>Every account belongs to one business and is only visible inside it.
 * Usernames stay globally unique (that is what the login page relies on), so
 * the uniqueness check is deliberately not scoped.
 */
@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return repository.findAllByBusinessIdOrderByRoleAscFullNameAsc(tenant());
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return repository.findByIdAndBusinessId(id, tenant())
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return repository.findByUsernameIgnoreCaseAndBusinessId(username, tenant())
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public long countOwners() {
        return repository.countByRoleAndActiveTrueAndBusinessId(Role.OWNER, tenant());
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
        if (losesOwnerAccess && repository.countByRoleAndActiveTrueAndBusinessId(Role.OWNER, tenant()) <= 1) {
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
        if (!active && user.isOwner()
                && repository.countByRoleAndActiveTrueAndBusinessId(Role.OWNER, tenant()) <= 1) {
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
