import { Checkbox, FormControl, FormControlLabel, FormHelperText } from '@mui/material'
import type { ChangeEventHandler } from 'react'
import type { BaseFormField } from './formTypes.ts'

type FormCheckboxFieldProps = BaseFormField & {
    defaultChecked?: boolean
    checked?: boolean
    value?: string
    onChange?: ChangeEventHandler<HTMLInputElement>
}

function FormCheckboxField({
    name,
    label,
    helperText,
    required,
    disabled,
    error,
    defaultChecked,
    checked,
    value,
    onChange,
}: FormCheckboxFieldProps) {
    return (
        <FormControl required={required} disabled={disabled} error={error}>
            <FormControlLabel
                control={
                    <Checkbox
                        name={name}
                        checked={checked}
                        defaultChecked={checked === undefined ? defaultChecked : undefined}
                        value={value}
                        onChange={onChange}
                    />
                }
                label={label}
            />

            {helperText && (
                <FormHelperText sx={{ ml: 0 }}>
                    {helperText}
                </FormHelperText>
            )}
        </FormControl>
    )
}

export default FormCheckboxField
