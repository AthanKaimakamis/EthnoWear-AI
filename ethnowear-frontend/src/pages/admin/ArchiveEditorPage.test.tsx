import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/http'
import { renderApp } from '../../test/render'
import ArchiveEditorPage from './ArchiveEditorPage'

const apiMocks = vi.hoisted(() => ({
    createFullArchiveEntry: vi.fn(),
    updateFullArchiveEntry: vi.fn(),
    getAdminArchiveItemDetail: vi.fn(),
    getPublicationReadiness: vi.fn(),
    runPublicationCommand: vi.fn(),
    sourcesFindAll: vi.fn(),
    referencesFindAll: vi.fn(),
    assetsFindAll: vi.fn(),
    getFullReference: vi.fn(),
}))

vi.mock('../../api/ArchiveAdminApi', async importOriginal => ({
    ...await importOriginal<typeof import('../../api/ArchiveAdminApi')>(),
    createFullArchiveEntry: apiMocks.createFullArchiveEntry,
    updateFullArchiveEntry: apiMocks.updateFullArchiveEntry,
    getAdminArchiveItemDetail: apiMocks.getAdminArchiveItemDetail,
    getPublicationReadiness: apiMocks.getPublicationReadiness,
    runPublicationCommand: apiMocks.runPublicationCommand,
    sourcesApi: { findAll: apiMocks.sourcesFindAll },
    sourceReferencesApi: { findAll: apiMocks.referencesFindAll },
    mediaAssetsApi: { findAll: apiMocks.assetsFindAll },
}))

vi.mock('../../api/ReferenceApi', () => ({
    getFullReference: apiMocks.getFullReference,
}))

beforeEach(() => {
    apiMocks.sourcesFindAll.mockResolvedValue({ content: [] })
    apiMocks.referencesFindAll.mockResolvedValue({ content: [] })
    apiMocks.assetsFindAll.mockResolvedValue({ content: [] })
    apiMocks.getFullReference.mockResolvedValue({
        regions: [], regionGroups: [], regionalEmbroideryTypes: [],
        techniques: [], ornaments: [], motifs: [], colors: [],
    })
})

describe('ArchiveEditorPage', () => {
    it('preserves unsaved draft fields when aggregate save fails', async () => {
        apiMocks.createFullArchiveEntry.mockRejectedValue(new ApiError(
            'Request failed with status 400',
            400,
            { message: 'Validation failed', fields: { sourceReferenceId: 'must be selected' } },
        ))
        const user = userEvent.setup()
        renderApp(<ArchiveEditorPage itemId={null} />)

        const title = await screen.findByRole('textbox', { name: /Bulgarian title/i })
        await user.clear(title)
        await user.type(title, 'Unsaved curator title')
        await user.click(screen.getByRole('button', { name: 'Save draft' }))

        await waitFor(() => expect(apiMocks.createFullArchiveEntry).toHaveBeenCalledOnce())
        expect(title).toHaveValue('Unsaved curator title')
        expect(screen.getByText('Validation failed')).toBeVisible()
        expect(screen.getByText('sourceReferenceId: must be selected')).toBeVisible()
    })
})
