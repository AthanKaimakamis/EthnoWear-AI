package fmi.ethnowear.util;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Sort;

import java.util.Comparator;
import java.util.Map;

public final class InMemorySortUtils {

    private InMemorySortUtils() {

    }

    public static <T> @NonNull Comparator<T> toComparator(
            @NonNull Sort sort,
            Map<String, Comparator<T>> supportedProperties,
            Comparator<T> defaultComparator,
            Comparator<T> tieBreaker
    ) {
        Comparator<T> result = null;

        for(Sort.Order order : sort) {
            Comparator<T> propertyComparator = supportedProperties.get(order.getProperty());

            if(propertyComparator == null)
                throw new IllegalArgumentException("Unsupported sort property: " + order.getProperty());

            if(order.isDescending())
                propertyComparator = propertyComparator.reversed();

            result = result == null
                    ? propertyComparator
                    : result.thenComparing(propertyComparator);
        }

        if(result == null)
            result = defaultComparator;

        return result.thenComparing(tieBreaker);
    }
}
