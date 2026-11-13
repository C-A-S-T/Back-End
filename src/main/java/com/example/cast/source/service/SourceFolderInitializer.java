package com.example.cast.source.service;

import com.example.cast.source.entity.SourceFolder;
import com.example.cast.source.entity.SourceFolderCategory;
import com.example.cast.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creates missing default folders within the same transaction as user creation. */
@Service
@RequiredArgsConstructor
public class SourceFolderInitializer {
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(User user) {
        if (user == null || user.getUserId() == null) {
            throw new IllegalArgumentException("A persisted user is required");
        }
        List<SourceFolder> existing = em.createQuery(
                "select f from SourceFolder f where f.user.userId = :userId", SourceFolder.class)
                .setParameter("userId", user.getUserId())
                .getResultList();

        for (SourceFolderCategory category : SourceFolderCategory.values()) {
            if (existing.stream().anyMatch(folder -> folder.getFolderCategory() == category)) {
                continue;
            }
            SourceFolder byName = existing.stream()
                    .filter(folder -> folder.getFolderName().equals(category.getDisplayName()))
                    .findFirst().orElse(null);
            if (byName != null) {
                byName.assignDefaultCategory(category);
            } else {
                em.persist(SourceFolder.defaultFolder(user, category));
            }
        }
    }
}
