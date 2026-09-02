import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../../test/render'
import type { SourceDetails } from '../../../types/archive'
import DocumentUploadDialog from './DocumentUploadDialog'
import * as documentApi from '../../../api/DocumentAdminApi'
import { sourceReferencesApi } from '../../../api/ArchiveAdminApi'

vi.mock('../../../api/DocumentAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/DocumentAdminApi')>(),
    uploadPdfDocument: vi.fn(),
    uploadStandaloneCapture: vi.fn(),
}))
vi.mock('../../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../../api/ArchiveAdminApi')>(),
    sourceReferencesApi: { create: vi.fn() },
}))

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

    it('creates a general reference and assigns it as the document default', async () => {
        const user = userEvent.setup()
        vi.mocked(sourceReferencesApi.create).mockResolvedValue({
            id: 19, sourceId: source.id, chapter: null, pageFrom: null, pageTo: null,
            figureNumber: null, sectionTitle: null, catalogNumber: null, referenceUrl: null,
            accessedDate: null, locator: null, note: null, createdAt: '', updatedAt: '',
        })
        vi.mocked(documentApi.uploadPdfDocument).mockResolvedValue({ documentId: 3, mediaAssetId: 4, documentPageId: null, documentPageMediaId: null, processingJobId: 5 })
        const uploaded = vi.fn()
        renderApp(<DocumentUploadDialog open sources={[source]} references={[]} onClose={vi.fn()} onUploaded={uploaded} />)

        await user.click(screen.getByRole('combobox', { name: 'Known source' }))
        await user.click(screen.getByRole('option', { name: source.title }))
        const fileInput = document.querySelector('input[type="file"][accept="application/pdf"]') as HTMLInputElement
        await user.upload(fileInput, new File(['pdf'], 'book.pdf', { type: 'application/pdf' }))
        await user.click(screen.getByRole('button', { name: 'Upload' }))

        await waitFor(() => expect(sourceReferencesApi.create).toHaveBeenCalledWith(expect.objectContaining({ sourceId: source.id })))
        expect(documentApi.uploadPdfDocument).toHaveBeenCalledWith(expect.objectContaining({
            metadata: expect.objectContaining({ sourceId: source.id, defaultSourceReferenceId: 19 }),
        }), expect.any(File), null, expect.any(Function))
        expect(uploaded).toHaveBeenCalled()
    })
})
