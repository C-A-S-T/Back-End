package com.example.cast.user.repository;

import com.example.cast.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 기존 대소문자 혼합 이메일도 조회하되, 중복된 기존 계정을 임의로 선택하지 않는다. */
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailIgnoreCase(String email);
    List<User> findAllByEmailIgnoreCase(String email);
}
