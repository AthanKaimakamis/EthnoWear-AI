package fmi.ethnowear.api.controller;

import fmi.ethnowear.api.dto.analysis.AnalyzeRequest;
import fmi.ethnowear.api.dto.analysis.AnalyzeResponse;
import fmi.ethnowear.application.service.InterpretationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/analyze")
public class AnalyzeController {

    private final InterpretationService interpretationService;

    public AnalyzeController(InterpretationService interpretationService) {
        this.interpretationService = interpretationService;
    }

    @PostMapping
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request){
        return interpretationService.analyze(request);
    }
}
