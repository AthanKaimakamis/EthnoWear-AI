import { useState } from 'react'
import {
    Box,
    Button,
    IconButton,
    List,
    ListItem,
    ListItemText,
    Stack,
    TextField,
    Tooltip,
    Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import CheckIcon from '@mui/icons-material/Check'
import CloseIcon from '@mui/icons-material/Close'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import { useTranslation } from 'react-i18next'

type Props = {
    name: string
    label: string
    value: string[]
    helperText?: string
    disabled?: boolean
    onChange: (value: string[]) => void
}

function FormStringListField(props: Props) {
    const { t } = useTranslation()
    const [draft, setDraft] = useState('')
    const [editingIndex, setEditingIndex] = useState<number | null>(null)
    const [editingValue, setEditingValue] = useState('')

    function isDuplicate(candidate: string, ignoredIndex?: number) {
        return props.value.some((value, index) => (
            index !== ignoredIndex && value.toLocaleLowerCase() === candidate.toLocaleLowerCase()
        ))
    }

    function addValue() {
        const nextValue = draft.trim()
        if (!nextValue || isDuplicate(nextValue)) return

        props.onChange([...props.value, nextValue])
        setDraft('')
    }

    function startEditing(index: number) {
        setEditingIndex(index)
        setEditingValue(props.value[index])
    }

    function saveEdit() {
        if (editingIndex === null) return

        const nextValue = editingValue.trim()
        if (!nextValue || isDuplicate(nextValue, editingIndex)) return

        props.onChange(props.value.map((value, index) => (
            index === editingIndex ? nextValue : value
        )))
        cancelEdit()
    }

    function cancelEdit() {
        setEditingIndex(null)
        setEditingValue('')
    }

    function removeValue(indexToRemove: number) {
        props.onChange(props.value.filter((_, index) => index !== indexToRemove))
        if (editingIndex === indexToRemove) cancelEdit()
    }

    return (
        <Box sx={{ minWidth: 0 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                <TextField
                    fullWidth
                    label={props.label}
                    value={draft}
                    disabled={props.disabled}
                    helperText={props.helperText}
                    onChange={event => setDraft(event.target.value)}
                    onKeyDown={event => {
                        if (event.key === 'Enter') {
                            event.preventDefault()
                            addValue()
                        }
                    }}
                />
                <Button
                    variant="outlined"
                    startIcon={<AddIcon />}
                    disabled={props.disabled || !draft.trim() || isDuplicate(draft.trim())}
                    onClick={addValue}
                    sx={{ minHeight: 56, flexShrink: 0 }}
                >
                    {t('admin.add')}
                </Button>
            </Stack>

            {props.value.length > 0 && (
                <List dense disablePadding sx={{ mt: 1, border: 1, borderColor: 'divider' }}>
                    {props.value.map((value, index) => (
                        <ListItem
                            key={`${value}-${index}`}
                            divider={index < props.value.length - 1}
                            secondaryAction={editingIndex === index ? (
                                <Stack direction="row" spacing={0.5}>
                                    <Tooltip title={t('admin.list.saveEdit')}>
                                        <span>
                                            <IconButton
                                                size="small"
                                                color="primary"
                                                disabled={!editingValue.trim() || isDuplicate(editingValue.trim(), index)}
                                                onClick={saveEdit}
                                            >
                                                <CheckIcon fontSize="small" />
                                            </IconButton>
                                        </span>
                                    </Tooltip>
                                    <Tooltip title={t('admin.cancel')}>
                                        <IconButton size="small" onClick={cancelEdit}>
                                            <CloseIcon fontSize="small" />
                                        </IconButton>
                                    </Tooltip>
                                </Stack>
                            ) : (
                                <Stack direction="row" spacing={0.5}>
                                    <Tooltip title={t('admin.edit')}>
                                        <IconButton size="small" disabled={props.disabled} onClick={() => startEditing(index)}>
                                            <EditOutlinedIcon fontSize="small" />
                                        </IconButton>
                                    </Tooltip>
                                    <Tooltip title={t('admin.list.remove')}>
                                        <IconButton size="small" color="error" disabled={props.disabled} onClick={() => removeValue(index)}>
                                            <DeleteOutlineIcon fontSize="small" />
                                        </IconButton>
                                    </Tooltip>
                                </Stack>
                            )}
                            sx={{ minHeight: 48, pr: 10 }}
                        >
                            {editingIndex === index ? (
                                <TextField
                                    fullWidth
                                    size="small"
                                    value={editingValue}
                                    onChange={event => setEditingValue(event.target.value)}
                                    onKeyDown={event => {
                                        if (event.key === 'Enter') {
                                            event.preventDefault()
                                            saveEdit()
                                        }
                                        if (event.key === 'Escape') cancelEdit()
                                    }}
                                    error={Boolean(editingValue.trim() && isDuplicate(editingValue.trim(), index))}
                                    sx={{ mr: 1 }}
                                />
                            ) : (
                                <ListItemText
                                    primary={<Typography variant="body2">{value}</Typography>}
                                />
                            )}
                            <input type="hidden" name={props.name} value={value} />
                        </ListItem>
                    ))}
                </List>
            )}
        </Box>
    )
}

export default FormStringListField
