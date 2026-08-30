import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../../test/render'
import SynchronizedTextDiff from './SynchronizedTextDiff'

function renderDiff(onApplySuggested?: () => void) {
    renderApp(
        <SynchronizedTextDiff
            original="old OCR text"
            suggested="new OCR text"
            originalTitle="Преди"
            suggestedTitle="След"
            synchronizedLabel="Синхронизирано сравнение"
            changesLabel={count => `${count} промени`}
            copySuggestedLabel="Копирай предложения текст"
            copiedSuggestedLabel="Текстът е копиран"
            applySuggestedLabel={onApplySuggested ? 'Приложи предложения текст' : undefined}
            onApplySuggested={onApplySuggested}
        />,
    )
}

describe('SynchronizedTextDiff', () => {
    it('renders both versions and highlights their changed parts', () => {
        renderDiff()

        expect(screen.getByText('Синхронизирано сравнение')).toBeInTheDocument()
        expect(screen.getByText('old')).toHaveStyle({ textDecoration: 'line-through' })
        expect(screen.getByText('new')).toBeInTheDocument()
    })

    it('keeps the two comparison panes at the same relative scroll position', () => {
        renderDiff()
        const original = screen.getByRole('region', { name: 'Преди' })
        const suggested = screen.getByRole('region', { name: 'След' })

        Object.defineProperties(original, {
            scrollHeight: { configurable: true, value: 1000 },
            clientHeight: { configurable: true, value: 200 },
        })
        Object.defineProperties(suggested, {
            scrollHeight: { configurable: true, value: 600 },
            clientHeight: { configurable: true, value: 200 },
        })
        original.scrollTop = 400

        fireEvent.scroll(original)

        expect(suggested.scrollTop).toBe(200)

        fireEvent.scroll(suggested)
        expect(original.scrollTop).toBe(400)

        suggested.scrollTop = 300
        fireEvent.scroll(suggested)
        expect(original.scrollTop).toBe(600)
    })

    it('copies the complete suggested text', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
        renderDiff()

        fireEvent.click(screen.getByRole('button', { name: 'Копирай предложения текст' }))

        expect(writeText).toHaveBeenCalledWith('new OCR text')
        expect(await screen.findByRole('button', { name: 'Текстът е копиран' })).toBeInTheDocument()
    })

    it('offers an explicit apply action when the caller supports it', () => {
        const onApply = vi.fn()
        renderDiff(onApply)

        fireEvent.click(screen.getByRole('button', { name: 'Приложи предложения текст' }))

        expect(onApply).toHaveBeenCalledOnce()
    })
})
