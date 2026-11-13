package com.example.cast.source.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(schema = "backend", name = "source_folder",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_source_folder_user_name",
                        columnNames = {"user_id", "folder_name"}
                ),
                @UniqueConstraint(
                        name = "uq_source_folder_user_id",
                        columnNames = {"user_id", "folder_id"}
                ),
                @UniqueConstraint(
                        name = "uq_source_folder_user_category",
                        columnNames = {"user_id", "folder_category"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceFolder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "folder_id")
    private Long folderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "folder_name", nullable = false, length = 120)
    private String folderName;

    // Existing user-created folders may retain NULL for backward compatibility.
    @Enumerated(EnumType.STRING)
    @Column(name = "folder_category", length = 30)
    private SourceFolderCategory folderCategory;

    public SourceFolder(User user, String folderName) {
        this.user = user;
        rename(folderName);
    }

    public static SourceFolder defaultFolder(User user, SourceFolderCategory category) {
        SourceFolder folder = new SourceFolder(user, category.getDisplayName());
        folder.folderCategory = category;
        return folder;
    }

    /** Reuse an existing same-named folder without changing its id or saved documents. */
    public void assignDefaultCategory(SourceFolderCategory category) {
        if (!category.getDisplayName().equals(folderName)) {
            throw new IllegalArgumentException("Default folder name and category do not match");
        }
        if (folderCategory != null && folderCategory != category) {
            throw new IllegalStateException("Folder already has a different default category");
        }
        folderCategory = category;
    }

    public void rename(String folderName) {
        if (folderCategory != null && !folderCategory.getDisplayName().equals(folderName)) {
            throw new IllegalStateException("Default folders cannot be renamed");
        }
        this.folderName = folderName;
    }
}
