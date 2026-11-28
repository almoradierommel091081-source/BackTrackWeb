package org.example.backtrackweb.config;

import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.tenant.TenantUserDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells Spring Security how to find an account and what role it has.
 * Deactivated accounts are reported as disabled, so they cannot log in.
 *
 * <p>The principal carries the account's business, which is where every request
 * gets its tenant from. Usernames are globally unique, so this lookup is the
 * one place that runs before a tenant exists.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No account named " + username));

        return new TenantUserDetails(user);
    }
}
