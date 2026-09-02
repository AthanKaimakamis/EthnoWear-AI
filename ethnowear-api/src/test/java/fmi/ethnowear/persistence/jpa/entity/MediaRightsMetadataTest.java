package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.rights.RightsStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaRightsMetadataTest {

    @Test
    void sourceAllowsClearedLicensedPublicDisplay() {
        Source source = new Source();

        source.updateRights(RightsStatus.LICENSED, " CC BY 4.0 ", true);

        assertThat(source.getRightsStatus()).isEqualTo(RightsStatus.LICENSED);
        assertThat(source.getLicense()).isEqualTo("CC BY 4.0");
        assertThat(source.isPublicDisplayAllowed()).isTrue();
    }

    @Test
    void mediaRejectsPublicDisplayWithoutClearance() {
        MediaAsset asset = new MediaAsset();

        assertThatThrownBy(() -> asset.updateRights(
                RightsStatus.UNKNOWN,
                null,
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rights status does not permit public display");
    }

    @Test
    void licensedContentRequiresLicenseText() {
        Source source = new Source();
        MediaAsset asset = new MediaAsset();

        assertThatThrownBy(() -> source.updateRights(
                RightsStatus.LICENSED,
                " ",
                false
        )).hasMessage("License is required for licensed content");

        assertThatThrownBy(() -> asset.updateRights(
                RightsStatus.LICENSED,
                null,
                false
        )).hasMessage("License is required for licensed content");
    }
}
