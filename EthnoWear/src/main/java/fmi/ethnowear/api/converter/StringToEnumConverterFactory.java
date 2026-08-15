package fmi.ethnowear.api.converter;

import fmi.ethnowear.domain.annotation.EnumAlias;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;

@Component
@SuppressWarnings({"rawtypes"})
public class StringToEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> @NonNull Converter<String, T> getConverter(@NonNull Class<T> targetType) {
        return source -> convert(source, targetType);
    }

    @Contract("null, _ -> fail")
    private <T extends Enum> @NonNull T convert(String source, Class<T> targetType) {
        if (source == null || source.isBlank())
            throw new IllegalArgumentException(targetType.getSimpleName() + " is required");

        String value = source.trim();

        return Arrays.stream(targetType.getEnumConstants())
                .filter(constant -> matches(constant, value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported " + targetType.getSimpleName() + ": " + source));
    }

    private boolean matches(@NonNull Enum<?> constant, String value) {
        if (constant.name().equalsIgnoreCase(value))
            return true;

        EnumAlias annotation = getAliasAnnotation(constant);

        return annotation != null && Arrays.stream(annotation.value())
                .anyMatch(alias -> alias.equalsIgnoreCase(value));
    }

    private EnumAlias getAliasAnnotation(@NonNull Enum<?> constant) {
        try {
            Field field = constant
                    .getDeclaringClass()
                    .getField(constant.name());

            return field.getAnnotation(EnumAlias.class);
        } catch (NoSuchFieldException ex) {
            throw new IllegalArgumentException("Cannot inspect enum constant: " + constant.name(), ex);
        }
    }
}
