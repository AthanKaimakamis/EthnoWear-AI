import Form from "../components/forms/Form.tsx";
import FormTextField from "../components/forms/FormTextField.tsx";
import FormCheckboxField from "../components/forms/FormCheckboxField.tsx";
import {initialFormState, type FormState, type SelectOption} from "../components/forms/formTypes.ts";
import {useActionState} from "react";
import FormSelectField from "../components/forms/FormSelectField.tsx";


const archiveTypeOptions: SelectOption[] = [
    {label: 'Embroidery sample', value: 'EMBROIDERY_SAMPLE'},
    {label: 'Ornament example', value: 'ORNAMENT_EXAMPLE'},
    {label: 'Text reference', value: 'TEXT_REFERENCE'},
]

async function submitArchiveItem(
    _previousState: FormState,
    formData: FormData,
): Promise<FormState> {
    const titleBg = getString(formData, "titleBg");
    const archiveType = getString(formData, "archiveType");
    const trusted = formData.get("trusted") === 'on';

    if (!titleBg) {
        return {
            status: 'error',
            message: 'Title BG is required.'
        }
    }

    console.log(titleBg, archiveType, trusted);

    await new Promise(resolve => setTimeout(resolve, 800));

    return {
        status: 'success',
        message: 'Archive saved successfully!'
    }
}

function getString(formData: FormData, key: string) {
    const value = formData.get(key)
    return typeof value === 'string' ? value.trim() : ''
}

function MaterialUITestPage() {
    const [state, formAction, isPending] = useActionState(
        submitArchiveItem,
        initialFormState,
    )
    return (
        <Form
            title="Add Archive Item"
            subtitle="Create source-backed archive evidence."
            state={state}
            isPending={isPending}
            formAction={formAction}
            submitLabel="Add Archive Item"
        >
            <FormTextField
                name="titleBg"
                label="Title BG"
                required
                disabled={isPending}
            />
            <FormTextField
                name="titleEn"
                label="Title EN"
                disabled={isPending}
            />
            <FormTextField
                name="description"
                label="Description"
                multiline
                minRows={4}
                disabled={isPending}
            />
            <FormSelectField
                name="archiveType"
                label="Archive Type"
                options={archiveTypeOptions}
                required
                disabled={isPending}
            />
            <FormCheckboxField
                name="trusted"
                label="Trusted source"
                disabled={isPending}
            />
        </Form>
    );
}

export default MaterialUITestPage