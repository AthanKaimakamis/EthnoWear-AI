import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'
import ArchiveWorkspaceDialog from './ArchiveWorkspaceDialog'

describe('ArchiveWorkspaceDialog', () => {
    it('uses the full archive workspace while retaining the public navigation area', () => {
        renderApp(<ArchiveWorkspaceDialog labelledBy="detail-title" onClose={vi.fn()}><h1 id="detail-title">Detail</h1></ArchiveWorkspaceDialog>)

        expect(screen.getByRole('dialog', { name: 'Detail' })).toBeVisible()
        expect(document.querySelector('[data-archive-workspace-modal="true"]')).toBeInTheDocument()
    })
})
