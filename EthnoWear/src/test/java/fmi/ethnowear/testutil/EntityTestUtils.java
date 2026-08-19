package fmi.ethnowear.testutil;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import org.springframework.test.util.ReflectionTestUtils;

public final class EntityTestUtils {

    private EntityTestUtils() {
    }

    public static void setId(AppendOnlyEntity entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
