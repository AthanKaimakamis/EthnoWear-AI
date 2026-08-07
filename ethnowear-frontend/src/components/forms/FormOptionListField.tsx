import { useMemo, useState } from 'react'
import {
    Autocomplete,
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
import type { OptionCategory, SelectOption } from './formTypes.ts'

type Props = {
    name: string
    label: string
    options: SelectOption[]
    value: string[]
    categories?: OptionCategory[]
    categoryLabel?: string
    disabled?: boolean
    onChange: (value: string[]) => void
}

function optionLabel(option: SelectOption) {
    return option.label === option.value || option.label.includes(`(${option.value})`)
        ? option.label
        : `${option.label} (${option.value})`
}

function FormOptionListField(props: Props) {
    const { t } = useTranslation()
    const [draft, setDraft] = useState<SelectOption | null>(null)
    const [categoryDraft, setCategoryDraft] = useState<OptionCategory | null>(null)
    const [editingIndex, setEditingIndex] = useState<number | null>(null)
    const [editingDraft, setEditingDraft] = useState<SelectOption | null>(null)

    const selectedValues = useMemo(() => new Set(props.value), [props.value])
    const optionsByValue = useMemo(() => new Map(
        props.options.map((option) => [option.value, option])
    ), [props.options])

    const availableOptions = useMemo(() => props.options.filter(
        (option) => !selectedValues.has(option.value)
    ), [props.options, selectedValues])

    const availableCategories = useMemo(() => (props.categories ?? []).filter(
        (category) => category.optionValues.some((value) => !selectedValues.has(value))
    ), [props.categories, selectedValues])

    function addCategory(category: OptionCategory | null) {
        setCategoryDraft(category)
        if (!category) {
            return
        }

        const categoryValues = category.optionValues.filter(
            (value) => optionsByValue.has(value) && !selectedValues.has(value)
        )

        if (categoryValues.length > 0) {
            props.onChange([...props.value, ...categoryValues])
        }
        setCategoryDraft(null)
    }

    function addValue() {
        if (!draft || selectedValues.has(draft.value)) {
            return
        }

        props.onChange([...props.value, draft.value])
        setDraft(null)
    }

    function startEditing(index: number) {
        setEditingIndex(index)
        setEditingDraft(optionsByValue.get(props.value[index]) ?? null)
    }

    function saveEdit() {
        if (editingIndex === null || !editingDraft) {
            return
        }

        props.onChange(props.value.map((value, index) => (
            index === editingIndex ? editingDraft.value : value
        )))
        cancelEdit()
    }

    function cancelEdit() {
        setEditingIndex(null)
        setEditingDraft(null)
    }

    function removeValue(indexToRemove: number) {
        props.onChange(props.value.filter((_, index) => index !== indexToRemove))
        if (editingIndex === indexToRemove) {
            cancelEdit()
        }
    }

    function rowOptions(value: string) {
        const currentOption = optionsByValue.get(value)
        return currentOption
            ? [currentOption, ...availableOptions]
            : availableOptions
    }

    return (
        <Box sx={{ minWidth: 0 }}>
            {availableCategories.length > 0 && (
                <Autocomplete
                    fullWidth
                    sx={{ mb: 1 }}
                    options={availableCategories}
                    value={categoryDraft}
                    disabled={props.disabled}
                    isOptionEqualToValue={(left, right) => left.value === right.value}
                    getOptionLabel={optionLabel}
                    onChange={(_, value) => addCategory(value)}
                    renderInput={(params) => (
                        <TextField {...params} label={props.categoryLabel} />
                    )}
                />
            )}

            <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                <Autocomplete
                    fullWidth
                    options={availableOptions}
                    value={draft}
                    disabled={props.disabled}
                    isOptionEqualToValue={(left, right) => left.value === right.value}
                    getOptionLabel={optionLabel}
                    onChange={(_, value) => setDraft(value)}
                    renderInput={(params) => (
                        <TextField {...params} label={props.label} />
                    )}
                />

                <Button
                    variant="outlined"
                    startIcon={<AddIcon />}
                    disabled={props.disabled || !draft}
                    onClick={addValue}
                    sx={{ minHeight: 56, flexShrink: 0 }}
                >
                    {t('admin.add')}
                </Button>
            </Stack>

            {props.value.length > 0 && (
                <List dense disablePadding sx={{ mt: 1, border: 1, borderColor: 'divider' }}>
                    {props.value.map((value, index) => {
                        const option = optionsByValue.get(value)

                        return (
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
                                                    disabled={!editingDraft}
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
                                sx={{ minHeight: 56, pr: 10 }}
                            >
                                {editingIndex === index ? (
                                    <Autocomplete
                                        fullWidth
                                        size="small"
                                        options={rowOptions(value)}
                                        value={editingDraft}
                                        disabled={props.disabled}
                                        isOptionEqualToValue={(left, right) => left.value === right.value}
                                        getOptionLabel={optionLabel}
                                        onChange={(_, nextValue) => setEditingDraft(nextValue)}
                                        renderInput={(params) => (
                                            <TextField {...params} label={props.label} />
                                        )}
                                    />
                                ) : (
                                    <ListItemText
                                        primary={<Typography variant="body2">{option?.label ?? value}</Typography>}
                                        secondary={option?.label === value ? undefined : value}
                                    />
                                )}
                                <input type="hidden" name={props.name} value={value} />
                            </ListItem>
                        )
                    })}
                </List>
            )}
        </Box>
    )
}

export default FormOptionListField
