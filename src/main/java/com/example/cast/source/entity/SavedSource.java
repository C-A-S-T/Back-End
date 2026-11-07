package com.example.cast.source.entity;

import com.example.cast.user.entity.User;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.document.entity.Document;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "saved_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_saved_source_user_document",
                columnNames = {"user_id", "document_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedSource extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "saved_source_id")
    private Long savedSourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            updatable = false
    )
    private Document document;

    // Database enforces the composite foreign key:
    // (user_id, folder_id) -> source_folder(user_id, folder_id)
    //
    // The existing V1 DDL also provides:
    // ON DELETE SET NULL (folder_id)
    //
    // Do not add a conflicting single-column FK here.
    @Column(name = "folder_id")
    private Long folderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_task_id", updatable = false)
    private ResearchTask originTask;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "save_method",
            nullable = false,
            length = 10,
            updatable = false
    )
    private SaveMethod saveMethod;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    private SavedSource(
            User user,
            Document document,
            ResearchTask originTask,
            SaveMethod saveMethod
    ) {
        this.user = user;

        this.document = document;

        this.originTask = originTask;

        this.saveMethod = saveMethod;
    }

    public static SavedSource autoSave(
            User user,
            Document document,
            ResearchTask originTask
    ) {
        return new SavedSource(
                user,
                document,
                originTask,
                SaveMethod.AUTO
        );
    }

    public static SavedSource manualSave(
            User user,
            Document document
    ) {
        return new SavedSource(
                user,
                document,
                null,
                SaveMethod.MANUAL
        );
    }

    public void moveToFolder(Long folderId) {
        this.folderId = folderId;
    }

    public void updateFavorite(boolean favorite) {
        this.favorite = favorite;
    }
}
