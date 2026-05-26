package fmi.ethnowear.api.controller;

import fmi.ethnowear.api.dto.archive.CreateArchiveItemFullRequest;
import fmi.ethnowear.api.dto.archive.CreateArchiveItemResponse;
import fmi.ethnowear.application.service.archive.ArchiveAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequestMapping("/api/admin/archive-items")
@RequiredArgsConstructor
public class AdminArchiveController {

    private final ArchiveAdminService archiveAdminService;

    @PostMapping("/create-full")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateArchiveItemResponse createFullArchiveItem(@Valid @RequestBody CreateArchiveItemFullRequest request) {
        return archiveAdminService.createFullArchiveItem(request);
    }
}
