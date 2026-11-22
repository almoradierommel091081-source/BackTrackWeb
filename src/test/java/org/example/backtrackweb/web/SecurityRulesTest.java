package org.example.backtrackweb.web;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login, logout and the role rules that protect owner-only pages.
 */
@AutoConfigureMockMvc
class SecurityRulesTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createAccounts() {
        userRepository.save(account("boss", "Boss Owner", Role.OWNER, true));
        userRepository.save(account("staff", "Staff Member", Role.EMPLOYEE, true));
        userRepository.save(account("gone", "Deactivated Person", Role.EMPLOYEE, false));
    }

    private User account(String username, String fullName, Role role, boolean active) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(active);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        return user;
    }

    @Test
    void protectedPagesRequireLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ownerCanLogInAndReachTheDashboard() throws Exception {
        mockMvc.perform(formLogin("/login").user("boss").password("secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(formLogin("/login").user("boss").password("wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void deactivatedAccountCannotLogIn() throws Exception {
        mockMvc.perform(formLogin("/login").user("gone").password("secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void employeeCannotOpenAccountManagement() throws Exception {
        mockMvc.perform(get("/users").with(user("staff").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotOpenSettingsOrReports() throws Exception {
        mockMvc.perform(get("/settings").with(user("staff").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/reports").with(user("staff").roles("EMPLOYEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotAddProductsButCanViewThem() throws Exception {
        mockMvc.perform(get("/products").with(user("staff").roles("EMPLOYEE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/products").with(user("staff").roles("EMPLOYEE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanOpenAccountManagement() throws Exception {
        mockMvc.perform(get("/users").with(user("boss").roles("OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousVisitorSeesTheLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }
}
