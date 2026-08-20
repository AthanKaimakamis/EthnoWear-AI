import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../../test/render'
import type { SourceDetails } from '../../../types/archive'
import DocumentUploadDialog from './DocumentUploadDialog'

const source: SourceDetails = {
    id: 7,
    title: 'Bulgarian Embroidery',
    author: 'Ivan Koev',
    publisher: 'Academy Press',
    year: 1951,
    sourceType: 'BOOK',
    language: 'bg',
    filePath: null,
    url: null,
    isbn: null,
    notes: null,
    trusted: true,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
}

describe('DocumentUploadDialog', () => {
    it('inherits and locks bibliographic metadata from the selected source', async () => {
        const user = userEvent.setup()
        renderApp(
            <DocumentUploadDialog
                open
                sources={[source]}
                references={[]}
                onClose={vi.fn()}
                onUploaded={vi.fn()}
            />,
        )

        await user.click(screen.getByRole('combobox', { name: 'Known source' }))
        await user.click(screen.getByRole('option', { name: source.title }))

        expect(screen.getByLabelText(/^Title/)).toHaveValue(source.title)
        expect(screen.getByLabelText('Author')).toHaveValue(source.author)
        expect(screen.getByLabelText('Publisher')).toHaveValue(source.publisher)
        expect(screen.getByLabelText('Publication year')).toHaveValue(source.year)
        expect(screen.getByLabelText('Language')).toHaveValue(source.language)
        expect(screen.getByLabelText('Author')).toHaveAttribute('readonly')

        await user.click(screen.getByRole('checkbox', { name: 'Change details for this document' }))
        expect(screen.getByLabelText('Author')).not.toHaveAttribute('readonly')
    })
})
