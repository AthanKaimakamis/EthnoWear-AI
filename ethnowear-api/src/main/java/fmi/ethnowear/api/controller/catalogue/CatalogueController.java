package fmi.ethnowear.api.controller.catalogue;

import fmi.ethnowear.application.dto.catalogue.ConceptCatalogQueryDto;
import fmi.ethnowear.application.dto.catalogue.ConceptCatalogResultDetails;
import fmi.ethnowear.application.dto.catalogue.EntityDetailDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.service.catalogue.ConceptCatalogService;
import fmi.ethnowear.application.service.catalogue.EntityDetailService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalogue")
public class CatalogueController {

    private final ConceptCatalogService catalogService;
    private final EntityDetailService entityDetailService;

    public CatalogueController(ConceptCatalogService catalogService, EntityDetailService entityDetailService) {
        this.catalogService = catalogService;
        this.entityDetailService = entityDetailService;
    }

    @PostMapping("/search")
    public ConceptCatalogResultDetails search(@RequestBody ConceptCatalogQueryDto query,
                                              @PageableDefault(size = 24, sort = "label") Pageable pageable
    ) {
        return catalogService.search(query, pageable);
    }

    @GetMapping("/{entityType}/{localName}")
    public EntityDetailDetails getDetails(@PathVariable FeatureType entityType,
                                          @PathVariable String localName,
                                          @RequestParam(defaultValue = "bg") String language,
                                          @PageableDefault(
                                                  size = 12,
                                                  sort = "id",
                                                  direction = Sort.Direction.DESC
                                          ) Pageable evidencePageable
    ) {
        return entityDetailService.findByLocalName(
                entityType,
                localName,
                language,
                evidencePageable
        );
    }
}
