import type {SelectOption} from "./formTypes.ts";
import {Autocomplete, TextField} from "@mui/material";

type Props = {
    name: string
    label: string
    options: SelectOption[]
    value: SelectOption[]
    disabled?: boolean
    onChange: (value: SelectOption[]) => void
}

function FormMultiSelectField(props: Props) {
    return (
        <>
            <Autocomplete
                multiple
                options={props.options}
                value={props.value}
                disabled={props.disabled}
                isOptionEqualToValue={(a, b) => a.value === b.value}
                onChange={(_, value) => props.onChange(value)}
                renderInput={(params) => (
                    <TextField {...params} label={props.label} />
                )}
            />

            {props.value.map(options => (
                <input
                    key={options.value}
                    type="hidden"
                    name={props.name}
                    value={options.value}
                />
            ))}
        </>
    )
}

export default FormMultiSelectField