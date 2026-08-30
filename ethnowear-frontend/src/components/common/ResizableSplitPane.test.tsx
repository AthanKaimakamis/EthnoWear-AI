import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../../test/render'
import ResizableSplitPane from './ResizableSplitPane'

describe('ResizableSplitPane', () => {
    it('supports keyboard resizing through its accessible separator', () => {
        renderApp(<ResizableSplitPane first={<div>Image</div>} second={<div>Text</div>} label="Resize panels" />)
        const separator = screen.getByRole('separator', { name: 'Resize panels' })

        expect(separator).toHaveAttribute('aria-valuenow', '40')
        fireEvent.keyDown(separator, { key: 'ArrowRight' })
        expect(separator).toHaveAttribute('aria-valuenow', '42')
        fireEvent.keyDown(separator, { key: 'ArrowLeft' })
        expect(separator).toHaveAttribute('aria-valuenow', '40')
    })
})
