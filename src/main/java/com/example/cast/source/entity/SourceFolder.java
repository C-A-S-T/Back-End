package com.example.cast.source.entity;

import com.example.cast.user.entity.User;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "source_folder",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_source_folder_user_name",
                        columnNames = {"user_id", "folder_name"}
                ),
                @UniqueConstraint(
                        name = "uq_source_folder_user_id",
                        columnNames = {"user_id", "folder_id"}
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
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @Column(
            name = "folder_name",
            nullable = false,
            length = 120
    )
    private String folderName;

    public SourceFolder(User user, String folderName) {
        this.user = user;

        rename(folderName);
    }

    public void rename(String folderName) {
        this.folderName = folderName;
    }
}
