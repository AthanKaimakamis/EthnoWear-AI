package fmi.ethnowear.persistence.jpa.projection;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;

public interface ConversationArchiveCardProjection {

    Long getArchiveItemId();

    String getTitleBg();

    String getTitleEn();

    String getDescriptionBg();

    String getDescriptionEn();

    ArchiveType getArchiveType();

    String getPeriodText();

    String getOriginText();

    String getCurrentLocation();

    TrustedLevel getTrustedLevel();

    Long getSourceReferenceId();
}
