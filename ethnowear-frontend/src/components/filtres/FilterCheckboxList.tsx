import {Checkbox, FormControlLabel, Stack, Typography} from "@mui/material";
import type {DisabledEmbroideryFilters, EmbroideryFilters} from "../../types/embroideryFilters.ts";
import type {ReferenceResource} from "../../types/reference.ts";

type FilterKey = keyof EmbroideryFilters

type FilterCheckboxListProps = {
    filterKey: FilterKey
    items: ReferenceResource[]
    filters: EmbroideryFilters
    disabledFilters?: DisabledEmbroideryFilters
    onToggle?: (filterKey: FilterKey, value: string) => void
}

function FilterCheckboxList({
    filterKey,
    items,
    filters,
    disabledFilters = {},
    onToggle
}: FilterCheckboxListProps) {
    function isChecked(value: string) {
        return filters[filterKey].includes(value)
    }

    function isDisabled(value: string) {
        return disabledFilters[filterKey]?.includes(value) ?? false
    }

    return (
        <Stack spacing={0.25}>
            {items.map((item) => (
                <FormControlLabel
                    key={item.localName}
                    disabled={isDisabled(item.localName)}
                    control={
                        <Checkbox
                            size="small"
                            checked={isChecked(item.localName)}
                            onChange={() => onToggle && onToggle(filterKey, item.localName)}
                            sx={{ p: 0.75 }}
                        />
                    }
                    label={
                        <Typography
                            variant="body2"
                            title={item.localName}
                            sx={{
                                lineHeight: 1.3,
                                textAlign: 'left',
                                overflowWrap: 'anywhere',
                            }}
                        >
                            {item.label || item.localName}
                        </Typography>
                    }
                    sx={{
                        width: '100%',
                        m: 0,
                        alignItems: 'center',
                        '& .MuiFormControlLabel-label': {
                            flex: 1,
                            minWidth: 0,
                        },
                    }}
                />
            ))}
        </Stack>
    )
}

export default FilterCheckboxList
