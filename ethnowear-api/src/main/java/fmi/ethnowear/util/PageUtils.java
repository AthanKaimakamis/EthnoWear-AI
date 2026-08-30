package fmi.ethnowear.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public final class PageUtils {

    private PageUtils() {
    }

    @Contract("_, _ -> new")
    public static <T> @NonNull Page<T> getPage(@NotNull List<T> matches, @NonNull Pageable pageable) {
        if (pageable.isUnpaged())
            return new PageImpl<>(matches);

        int from = (int) Math.min(pageable.getOffset(), matches.size());
        int to = Math.min(from + pageable.getPageSize(), matches.size());

        return new PageImpl<>(
                matches.subList(from, to),
                pageable,
                matches.size()
        );
    }
}
