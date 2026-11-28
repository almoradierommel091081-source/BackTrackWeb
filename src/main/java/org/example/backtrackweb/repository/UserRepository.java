package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Login lookup. Usernames are globally unique, so this runs before a tenant
     * is known; it is the only lookup that is not business scoped and it is
     * used for authentication only.
     */
    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByIdAndBusinessId(Long id, Long businessId);

    Optional<User> findByUsernameIgnoreCaseAndBusinessId(String username, Long businessId);

    Optional<User> findByUuidAndBusinessId(String uuid, Long businessId);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByBusinessIdOrderByRoleAscFullNameAsc(Long businessId);

    long countByRoleAndActiveTrueAndBusinessId(Role role, Long businessId);

    long countByRoleAndBusinessId(Role role, Long businessId);
}
