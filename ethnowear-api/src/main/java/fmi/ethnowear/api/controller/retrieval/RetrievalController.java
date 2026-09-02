package fmi.ethnowear.api.controller.retrieval;

import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedRetrievalQuery;
import fmi.ethnowear.application.service.retrieval.RagRetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Grounded retrieval",
        description = "Search approved Bulgarian evidence with exact citations."
)
@RestController
@RequestMapping("/api/admin/retrieval")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ethnowear.retrieval",
        name = "enabled",
        havingValue = "true"
)
public class RetrievalController {

    private final RagRetrievalService retrievalService;

    @Operation(
            summary = "Search approved evidence",
            description = """
                    Embeds the Bulgarian question, searches the rebuildable vector index,
                    reloads authoritative SQL chunks, rejects stale or unapproved evidence,
                    and returns ranked excerpts with exact page citations.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retrieval completed"),
            @ApiResponse(responseCode = "400", description = "Invalid query"),
            @ApiResponse(responseCode = "503", description = "Retrieval dependency unavailable")
    })
    @PostMapping("/search")
    public GroundedRetrievalDetails search(@Valid @RequestBody GroundedRetrievalQuery query) {
        return retrievalService.retrieve(query);
    }
}
