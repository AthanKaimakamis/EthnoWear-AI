package fmi.ethnowear.api.controller;

import fmi.ethnowear.application.service.archive.source.SourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/admin/archive-items")
@RequiredArgsConstructor
public class AdminArchiveController {

    private final SourceService sourceService;

}
