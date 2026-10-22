package com.ecommerce.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.Role;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    // Admin: tìm kiếm theo email (phân trang)
    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    // Admin: filter theo role
    Page<User> findByRole(Role role, Pageable pageable);

    // Admin: filter theo role + search email
    Page<User> findByRoleAndEmailContainingIgnoreCase(Role role, String email, Pageable pageable);

    // Admin: đếm theo role
    long countByRole(Role role);
}
