import { FormControl, FormHelperText, InputLabel, MenuItem, Select } from '@mui/material'
import type { SelectChangeEvent } from '@mui/material/Select'
import type { BaseFormField, SelectOption } from './formTypes.ts'

type FormSelectFieldProps = BaseFormField & {
    options: SelectOption[]
    defaultValue?: string
    value?: string
    onChange?: (event: SelectChangeEvent<string>) => void
}

function FormSelectField({
    name,
    label,
    helperText,
    required,
    disabled,
    error,
    options,
    defaultValue = '',
    value,
    onChange,
}: FormSelectFieldProps) {
    return (
        <FormControl
            fullWidth
            required={required}
            disabled={disabled}
            error={error}
        >
            <InputLabel id={`${name}-label`}>
                {label}
            </InputLabel>

            <Select
                labelId={`${name}-label`}
                name={name}
                label={label}
                value={value}
                defaultValue={value === undefined ? defaultValue : undefined}
                onChange={onChange}
            >
                {options.map((option) => (
                    <MenuItem key={option.value} value={option.value}>
                        {option.label}
                    </MenuItem>
                ))}
            </Select>

            {helperText && (
                <FormHelperText>
                    {helperText}
                </FormHelperText>
            )}
        </FormControl>
    )
}

export default FormSelectField
