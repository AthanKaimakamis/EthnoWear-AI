import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'
import PdfViewerContent from './PdfViewerContent'

vi.mock('react-pdf', async () => {
    const React = await import('react')
    return {
        pdfjs: { GlobalWorkerOptions: { workerSrc: '' } },
        Document: ({ children, onLoadSuccess }: { children: React.ReactNode; onLoadSuccess: (result: { numPages: number }) => void }) => {
            const loaded = React.useRef(false)
            React.useEffect(() => {
                if (!loaded.current) {
                    loaded.current = true
                    onLoadSuccess({ numPages: 3 })
                }
            }, [onLoadSuccess])
            return <div>{children}</div>
        },
        Page: ({ pageNumber, width, rotate }: { pageNumber: number; width: number; rotate: number }) => (
            <div data-testid="pdf-page" data-page={pageNumber} data-width={width} data-rotation={rotate} />
        ),
    }
})

describe('PdfViewerContent', () => {
    it('supports page navigation, zoom, and rotation', async () => {
        const user = userEvent.setup()
        renderApp(<PdfViewerContent source="/document.pdf" title="Document" onClose={vi.fn()} />)

        await waitFor(() => expect(screen.getByText('1 of 3')).toBeInTheDocument())
        await user.click(screen.getByRole('button', { name: 'Next page' }))
        expect(screen.getByTestId('pdf-page')).toHaveAttribute('data-page', '2')

        await user.click(screen.getByRole('button', { name: 'Zoom in' }))
        expect(screen.getByText('125%')).toBeInTheDocument()

        await user.click(screen.getByRole('button', { name: 'Rotate right' }))
        expect(screen.getByTestId('pdf-page')).toHaveAttribute('data-rotation', '90')
    })
})
