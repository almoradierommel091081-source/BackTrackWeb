package org.example.backtrackweb.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;

/**
 * Create / edit an employee or owner account.
 * The password is only required when the account is created.
 */
public class UserForm {

    private Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "Username may only use letters, numbers, dot, underscore or dash")
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(max = 120)
    private String fullName;

    @Email(message = "Enter a valid email address")
    @Size(max = 120)
    private String email;

    @NotNull(message = "Choose a role")
    private Role role = Role.EMPLOYEE;

    private boolean active = true;

    private boolean canManageCustomers = false;

    /** Optional when editing: leave blank to keep the current password. */
    @Size(max = 100)
    private String password;

    private String confirmPassword;

    public static UserForm fromEntity(User user) {
        UserForm form = new UserForm();
        form.id = user.getId();
        form.username = user.getUsername();
        form.fullName = user.getFullName();
        form.email = user.getEmail();
        form.role = user.getRole();
        form.active = user.isActive();
        form.canManageCustomers = user.isCanManageCustomers();
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isCanManageCustomers() {
        return canManageCustomers;
    }

    public void setCanManageCustomers(boolean canManageCustomers) {
        this.canManageCustomers = canManageCustomers;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
