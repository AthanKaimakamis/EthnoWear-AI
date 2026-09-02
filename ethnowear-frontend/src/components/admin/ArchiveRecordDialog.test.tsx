import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ArchiveRecordDialog, { type ArchiveField } from './ArchiveRecordDialog'

const fields: ArchiveField[] = [
    { name: 'rightsStatus', label: 'Rights status', section: 'Rights', kind: 'select', required: true, nullable: false, defaultValue: 'UNKNOWN', options: [
        { value: 'UNKNOWN', label: 'Unknown' }, { value: 'PUBLIC_DOMAIN', label: 'Public domain' },
        { value: 'LICENSED', label: 'Licensed' }, { value: 'RESTRICTED', label: 'Restricted' },
    ] },
    { name: 'license', label: 'License', section: 'Rights', visibleWhen: values => values.rightsStatus === 'LICENSED', requiredWhen: values => values.rightsStatus === 'LICENSED' },
    { name: 'publicDisplayAllowed', label: 'Public display allowed', section: 'Rights', kind: 'boolean', nullable: false, defaultValue: false, disabledWhen: values => values.rightsStatus === 'UNKNOWN' || values.rightsStatus === 'RESTRICTED' },
]

function chooseRights(label: string) {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /Rights status/ }))
    fireEvent.click(within(screen.getByRole('listbox')).getByText(label))
}

describe('rights controls', () => {
    it('defaults new records to unknown and private', () => {
        render(<ArchiveRecordDialog open title="Rights" fields={fields} record={null} saving={false} error={null} onClose={vi.fn()} onSubmit={vi.fn()} />)
        expect(screen.getByRole('combobox', { name: /Rights status/ })).toHaveTextContent('Unknown')
        expect(screen.getByRole('switch', { name: 'Public display allowed' })).toBeDisabled()
        expect(screen.getByRole('switch', { name: 'Public display allowed' })).not.toBeChecked()
        expect(screen.queryByLabelText('License')).not.toBeInTheDocument()
    })

    it('requires a nonblank license before saving licensed records', () => {
        const onSubmit = vi.fn()
        render(<ArchiveRecordDialog open title="Rights" fields={fields} record={{ id: 8, rightsStatus: 'LICENSED', license: null, publicDisplayAllowed: false } as never} saving={false} error={null} onClose={vi.fn()} onSubmit={onSubmit} />)
        expect(screen.getByRole('combobox', { name: /Rights status/ })).toHaveTextContent('Licensed')
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
        fireEvent.change(screen.getByRole('textbox', { name: /License/ }), { target: { value: 'CC BY-SA 4.0' } })
        fireEvent.click(screen.getByRole('switch', { name: 'Public display allowed' }))
        fireEvent.click(screen.getByRole('button', { name: 'Save' }))
        expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ rightsStatus: 'LICENSED', license: 'CC BY-SA 4.0', publicDisplayAllowed: true }))
    })

    it('forces public display off for restricted rights and displays linked-source warnings', () => {
        render(<ArchiveRecordDialog open title="Rights" fields={fields} record={{ id: 7, rightsStatus: 'LICENSED', license: 'CC BY 4.0', publicDisplayAllowed: true } as never} saving={false} error={null} onClose={vi.fn()} onSubmit={vi.fn()} warnings={values => values.publicDisplayAllowed ? ['Linked source is not publicly cleared.'] : []} />)
        expect(screen.getByText('Linked source is not publicly cleared.')).toBeInTheDocument()
        chooseRights('Restricted')
        expect(screen.getByRole('switch', { name: 'Public display allowed' })).toBeDisabled()
        expect(screen.getByRole('switch', { name: 'Public display allowed' })).not.toBeChecked()
        expect(screen.queryByText('Linked source is not publicly cleared.')).not.toBeInTheDocument()
        expect(screen.queryByLabelText('License')).not.toBeInTheDocument()
    })
})
