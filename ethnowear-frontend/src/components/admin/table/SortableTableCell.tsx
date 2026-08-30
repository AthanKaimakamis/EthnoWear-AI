import type { ReactNode } from 'react'
import { TableCell, TableSortLabel } from '@mui/material'

type Props = {
    active: boolean
    direction: 'asc' | 'desc'
    onClick: () => void
    children: ReactNode
}

export default function SortableTableCell({ active, direction, onClick, children }: Props) {
    return <TableCell sortDirection={active ? direction : false}>
        <TableSortLabel active={active} direction={active ? direction : 'asc'} onClick={onClick}>
            {children}
        </TableSortLabel>
    </TableCell>
}
