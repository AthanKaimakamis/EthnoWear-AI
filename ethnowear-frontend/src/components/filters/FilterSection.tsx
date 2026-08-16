import { Accordion, AccordionDetails, AccordionSummary, Stack, Typography } from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import type { ReferenceResource } from '../../types/reference'
import SearchableFilterList from './SearchableFilterList'

export type FilterSectionGroup = {
    key: string
    title?: string
    items: ReferenceResource[]
    selectedValues: string[]
    disabledValues?: string[]
    onToggle: (value: string) => void
}

type Props = {
    title: string
    groups: FilterSectionGroup[]
    defaultExpanded?: boolean
}

export default function FilterSection({ title, groups, defaultExpanded = true }: Props) {
    if (groups.every(group => group.items.length === 0)) return null

    return (
        <Accordion
            defaultExpanded={defaultExpanded}
            disableGutters
            elevation={0}
            sx={{ bgcolor: 'transparent', borderBottom: 1, borderColor: 'divider', '&::before': { display: 'none' } }}
        >
            <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ minHeight: 44, px: .5, '& .MuiAccordionSummary-content': { my: 1 } }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{title}</Typography>
            </AccordionSummary>
            <AccordionDetails sx={{ px: .5, pt: 0, pb: 1.5 }}>
                <Stack spacing={2}>
                    {groups.filter(group => group.items.length > 0).map(group => (
                        <SearchableFilterList
                            key={group.key}
                            title={group.title ?? title}
                            showTitle={Boolean(group.title)}
                            items={group.items}
                            selectedValues={group.selectedValues}
                            disabledValues={group.disabledValues}
                            onToggle={group.onToggle}
                        />
                    ))}
                </Stack>
            </AccordionDetails>
        </Accordion>
    )
}
