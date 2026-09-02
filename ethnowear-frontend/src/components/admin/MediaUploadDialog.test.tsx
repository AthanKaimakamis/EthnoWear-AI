import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'
import type { MediaAssetDetails } from '../../types/archive'
import MediaUploadDialog from './MediaUploadDialog'

const api = vi.hoisted(() => ({ upload: vi.fn(), update: vi.fn() }))

vi.mock('../../api/ArchiveAdminApi', async importOriginal => {
    const original = await importOriginal<typeof import('../../api/ArchiveAdminApi')>()
    return { ...original, uploadMediaAsset: api.upload, mediaAssetsApi: { ...original.mediaAssetsApi, update: api.update } }
})

const uploaded: MediaAssetDetails = {
    id: 42, sourceReferenceId: null, fileName: 'example.pdf', filePath: '/media/example.pdf', storageUrl: null,
    mimeType: 'application/pdf', mediaType: 'PDF', width: null, height: null, sizeBytes: 12, checksum: 'abc',
    thumbnailPath: null, description: null, rightsStatus: 'UNKNOWN', license: null, publicDisplayAllowed: false,
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', documentFigure: null,
}

describe('MediaUploadDialog rights', () => {
    beforeEach(() => {
        api.upload.mockReset().mockResolvedValue(uploaded)
        api.update.mockReset().mockImplementation(async (_id, input) => ({ ...uploaded, ...input }))
    })

    it('opens batch upload as a bounded modal instead of a full-screen workspace', () => {
        renderApp(<MediaUploadDialog open category="archive" sourceReferences={[]} sourceReferenceLabel={() => ''} onClose={vi.fn()} onUploaded={vi.fn()} />)

        expect(screen.getByRole('dialog')).toBeVisible()
        expect(screen.getByRole('dialog')).not.toHaveClass('MuiDialog-paperFullScreen')
    })

    it('explains rights and defaults uploads to unknown and private', async () => {
        const user = userEvent.setup()
        renderApp(<MediaUploadDialog open category="archive" sourceReferences={[]} sourceReferenceLabel={() => ''} onClose={vi.fn()} onUploaded={vi.fn()} />)

        expect(screen.getByRole('combobox', { name: 'Rights status' })).toHaveTextContent('Unknown rights')
        expect(screen.getByRole('switch', { name: 'Public display allowed' })).toBeDisabled()
        await user.click(screen.getByText('How rights affect public visibility'))
        expect(screen.getByText(/It is not known whether the material may be published/)).toBeVisible()
        expect(screen.getByText(/A file is public only when its status permits it/)).toBeVisible()
    })

    it('saves licensed rights after upload and requires an explicit license', async () => {
        const user = userEvent.setup()
        const onUploaded = vi.fn()
        renderApp(<MediaUploadDialog open category="archive" sourceReferences={[]} sourceReferenceLabel={() => ''} onClose={vi.fn()} onUploaded={onUploaded} />)
        await user.click(screen.getByRole('combobox', { name: 'Rights status' }))
        await user.click(within(screen.getByRole('listbox')).getByText('Licensed'))
        await user.type(screen.getByRole('textbox', { name: /License/ }), 'CC BY-SA 4.0')
        await user.click(screen.getByRole('switch', { name: 'Public display allowed' }))
        await user.upload(document.querySelector('input[type="file"]') as HTMLInputElement, new File(['pdf'], 'example.pdf', { type: 'application/pdf' }))
        await user.click(screen.getByRole('button', { name: 'Upload ready (1)' }))

        expect(api.upload).toHaveBeenCalledTimes(1)
        expect(api.update).toHaveBeenCalledWith(42, expect.objectContaining({ rightsStatus: 'LICENSED', license: 'CC BY-SA 4.0', publicDisplayAllowed: true }))
        expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ rightsStatus: 'LICENSED', publicDisplayAllowed: true }))
    })

    it('stages and uploads multiple files independently', async () => {
        const user = userEvent.setup()
        api.upload.mockResolvedValueOnce({ ...uploaded, id: 51, fileName: 'one.pdf' }).mockResolvedValueOnce({ ...uploaded, id: 52, fileName: 'two.pdf' })
        renderApp(<MediaUploadDialog open category="archive" sourceReferences={[]} sourceReferenceLabel={() => ''} onClose={vi.fn()} onUploaded={vi.fn()} />)
        await user.upload(document.querySelector('input[type="file"]') as HTMLInputElement, [
            new File(['one'], 'one.pdf', { type: 'application/pdf' }),
            new File(['two'], 'two.pdf', { type: 'application/pdf' }),
        ])
        expect(screen.getByText('one.pdf')).toBeVisible()
        expect(screen.getByText('two.pdf')).toBeVisible()
        await user.click(screen.getByRole('button', { name: 'Upload ready (2)' }))
        expect(api.upload).toHaveBeenCalledTimes(2)
        expect(api.update).toHaveBeenCalledTimes(2)
        expect(await screen.findAllByText('Complete')).toHaveLength(2)
    })
})
