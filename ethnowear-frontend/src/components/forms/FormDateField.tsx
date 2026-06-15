import {TextField} from "@mui/material";

type FormDateFieldProps = {
    value?: string
    defaultValue?: string
}

function FormDateField(props: FormDateFieldProps) {
    return (
        <TextField
            {...props}
            type="date"
            fullWidth
            slotProps={{ inputLabel: { shrink: true } }}
        />
    )
}

export default FormDateField