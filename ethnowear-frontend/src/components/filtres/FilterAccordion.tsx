import type {DisabledEmbroideryFilters, EmbroideryFilters} from "../../types/embroideryFilters.ts";
import type {ReferenceResource} from "../../types/reference.ts";
import {Accordion, AccordionDetails, AccordionSummary, Box, Stack, Typography} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import FilterCheckboxList from "./FilterCheckboxList.tsx";

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
                    maxHeight: 230,
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    px: 0.5,
                    pt: 0,
                    pb: 1.5,
                    scrollbarWidth: 'thin',
                    scrollbarColor: '#AEB4AE transparent',
                    '&::-webkit-scrollbar': { width: 6 },
                    '&::-webkit-scrollbar-thumb': {
                        bgcolor: '#AEB4AE',
                        borderRadius: 3,
                    },
                }}
            >
                <Stack spacing={2}>
                    {groups.map((group) => (
                        <Box key={group.filterKey}>
                            {group.title && (
                                <Typography
                                    variant="caption"
                                    color="text.secondary"
                                    sx={{
                                        display: 'block',
                                        mb: 0.5,
                                        textTransform: 'uppercase',
                                        fontWeight: 700
                                    }}
                                >
                                    {group.title}
                                </Typography>
                            )}

                            <FilterCheckboxList
                                filterKey={group.filterKey}
                                items={group.items}
                                filters={filters}
                                disabledFilters={disabledFilters}
                                onToggle={onToggle}
                            />
                        </Box>
                    ))}
                </Stack>
            </AccordionDetails>
        </Accordion>
    )
}

export default FilterAccordion
