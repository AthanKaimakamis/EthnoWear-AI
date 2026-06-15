import { TextField } from '@mui/material'
import type { ChangeEventHandler } from 'react'
import type { BaseFormField } from './formTypes.ts'

type FormTextFieldProps = BaseFormField & {
    multiline?: boolean
    minRows?: number
    placeholder?: string
    type?: 'text' | 'email' | 'password' | 'number' | 'url' | 'tel'
    value?: string
    defaultValue?: string
    autoComplete?: string
    onChange?: ChangeEventHandler<HTMLInputElement | HTMLTextAreaElement>
}

function FormTextField({
    name,
    label,
    helperText,
    required,
    disabled,
    error,
    multiline,
    minRows,
    placeholder,
    type = 'text',
    value,
    defaultValue,
    autoComplete,
    onChange,
}: FormTextFieldProps) {
    return (
        <TextField
            name={name}
            label={label}
            helperText={helperText}
            required={required}
            disabled={disabled}
            error={error}
            multiline={multiline}
            minRows={minRows}
            placeholder={placeholder}
            type={type}
            value={value}
            defaultValue={defaultValue}
            autoComplete={autoComplete}
            onChange={onChange}
            fullWidth
        />
    )
}

export default FormTextField
