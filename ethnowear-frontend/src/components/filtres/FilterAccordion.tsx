import type {DisabledEmbroideryFilters, EmbroideryFilters} from "../../types/embroideryFilters.ts";
import type {ReferenceResource} from "../../types/reference.ts";
import {Accordion, AccordionDetails, AccordionSummary, Stack, Typography} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SearchableFilterList from './SearchableFilterList.tsx'

type FilterKey = keyof EmbroideryFilters

type FilterAccordionGroup = {
    title?: string
    filterKey: FilterKey
    items: ReferenceResource[]
}

type FilterAccordionProps = {
    title: string
    groups: FilterAccordionGroup[]
    filters: EmbroideryFilters
    disabledFilters?: DisabledEmbroideryFilters
    defaultExpanded?: boolean
    onToggle: (filterKey: FilterKey, value: string) => void
}

function FilterAccordion({
    title,
    groups,
    filters,
    disabledFilters,
    defaultExpanded = true,
    onToggle
}: FilterAccordionProps) {
    return (
        <Accordion
            defaultExpanded={defaultExpanded}
            disableGutters
            elevation={0}
            sx={{
                bgcolor: 'transparent',
                borderBottom: 1,
                borderColor: 'divider',
                '&::before': { display: 'none' },
            }}
        >
            <AccordionSummary
                expandIcon={<ExpandMoreIcon />}
                sx={{
                    minHeight: 44,
                    px: 0.5,
                    '& .MuiAccordionSummary-content': { my: 1 },
                }}
            >
                <Typography variant="subtitle2" sx={{ fontWeight:700 }}>
                    {title}
                </Typography>
            </AccordionSummary>

            <AccordionDetails
                sx={{
                    px: 0.5,
                    pt: 0,
                    pb: 1.5,
                }}
            >
                <Stack spacing={2}>
                    {groups.map((group) => (
                        <SearchableFilterList
                            key={group.filterKey}
                            title={group.title ?? title}
                            showTitle={Boolean(group.title)}
                            items={group.items}
                            selectedValues={filters[group.filterKey]}
                            disabledValues={disabledFilters?.[group.filterKey]}
                            onToggle={value => onToggle(group.filterKey, value)}
                        />
                    ))}
                </Stack>
            </AccordionDetails>
        </Accordion>
    )
}

export default FilterAccordion
