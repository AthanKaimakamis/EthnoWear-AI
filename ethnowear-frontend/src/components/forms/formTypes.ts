export type FormStatus = 'idle' | 'success' | 'error'

export type FormState = {
    status: FormStatus
    message: string | null
}

export const initialFormState: FormState = {
    status: 'idle',
    message: null,
}

export type SelectOption = {
    label: string
    value: string
}

export type OptionCategory = SelectOption & {
    optionValues: string[]
}

export type BaseFormField = {
    name: string
    label: string
    helperText?: string
    required?: boolean
    disabled?: boolean
    error?: boolean
}
