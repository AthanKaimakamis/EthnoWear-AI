import { Autocomplete, TextField } from '@mui/material'
import type { SelectOption } from './formTypes'

type Props = {
    name: string
    label: string
    options: SelectOption[]
    value: SelectOption[]
    disabled?: boolean
    onChange: (value: SelectOption[]) => void
}

export default function FormMultiSelectField(props: Props) {
    return (
        <>
            <Autocomplete
                multiple
                options={props.options}
                value={props.value}
                disabled={props.disabled}
                isOptionEqualToValue={(left, right) => left.value === right.value}
                onChange={(_, value) => props.onChange(value)}
                renderInput={params => <TextField {...params} label={props.label} />}
            />
            {props.value.map(option => <input key={option.value} type="hidden" name={props.name} value={option.value} />)}
        </>
    )
}
