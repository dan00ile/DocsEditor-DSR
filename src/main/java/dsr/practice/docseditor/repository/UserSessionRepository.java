package dsr.practice.docseditor.repository;

import dsr.practice.docseditor.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Integer> {
    Optional<UserSession> findByToken(String token);
    void deleteByToken(String token);
}
