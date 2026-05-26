package fmi.ethnowear.api.controller;

import fmi.ethnowear.api.dto.reference.ReferenceItemDto;
import fmi.ethnowear.api.dto.reference.ReferenceResponse;
import fmi.ethnowear.application.service.reference.ReferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    private final ReferenceService referenceService;

    public ReferenceController(ReferenceService referenceService) {
        this.referenceService = referenceService;
    }

    @GetMapping("/full")
    public ReferenceResponse getFullReference(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getFullReference(language);
    }

    @GetMapping("/regions")
    public List<ReferenceItemDto> getRegions(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getRegions(language);
    }

    @GetMapping("/region-groups")
    public List<ReferenceItemDto> getRegionGroups(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getRegionGroups(language);
    }

    @GetMapping("/ornaments")
    public List<ReferenceItemDto> getOrnaments(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getOrnaments(language);
    }

    @GetMapping("/colors")
    public List<ReferenceItemDto> getColors(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getColors(language);
    }

    @GetMapping("/techniques")
    public List<ReferenceItemDto> getTechniques(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getTechniques(language);
    }

    @GetMapping("/motifs")
    public List<ReferenceItemDto> getMotifs(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getMotifs(language);
    }

    @GetMapping("/regional-embroidery-types")
    public List<ReferenceItemDto> getRegionalEmbroideryTypes(@RequestParam(defaultValue = "bg") String language) {
        return referenceService.getRegionalEmbroideryTypes(language);
    }
}
