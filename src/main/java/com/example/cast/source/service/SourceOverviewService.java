package com.example.cast.source.service;

import com.example.cast.source.dto.response.SourceOverviewResponse;
import com.example.cast.source.dto.response.SourceOverviewResponse.FolderOverview;
import com.example.cast.source.repository.SourceOverviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceOverviewService {
    private final SourceOverviewRepository overviewRepository;

    public SourceOverviewResponse getOverview(long userId) {
        var counts = overviewRepository.countSavedSources(userId);
        List<FolderOverview> folders = overviewRepository.countFixedFolders(userId)
                .stream()
                .map(folder -> new FolderOverview(
                        folder.folderId(),
                        folder.category(),
                        folder.folderName(),
                        folder.documentCount()
                ))
                .toList();

        return new SourceOverviewResponse(
                counts.total(),
                counts.automatic(),
                null, // Not available until sentence-scrap persistence is implemented.
                null, // Not available until policy-change persistence is implemented.
                folders.size(),
                counts.unclassified(),
                counts.legacyFolder(),
                folders
        );
    }
}
