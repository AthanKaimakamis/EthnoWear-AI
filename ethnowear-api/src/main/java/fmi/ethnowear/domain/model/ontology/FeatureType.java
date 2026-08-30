package fmi.ethnowear.domain.model.ontology;

import fmi.ethnowear.domain.annotation.EnumAlias;

public enum FeatureType {
    @EnumAlias({"ornament", "ornaments"})
    ORNAMENT,
    @EnumAlias({"color", "colors"})
    COLOR,
    @EnumAlias({"technique", "techniques"})
    TECHNIQUE,
    @EnumAlias({"motif", "motifs"})
    MOTIF,
    @EnumAlias({"region", "regions"})
    REGION,
    @EnumAlias({"regional-embroidery", "regional-embroideries", "regional_embroidery"})
    REGIONAL_EMBROIDERY
}
