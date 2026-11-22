package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByOrderByRoleAscFullNameAsc();

    long countByRoleAndActiveTrue(Role role);

    long countByRole(Role role);
}
