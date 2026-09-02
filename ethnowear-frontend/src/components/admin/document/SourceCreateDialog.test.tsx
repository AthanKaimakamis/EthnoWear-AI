import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../../test/render'
import SourceCreateDialog from './SourceCreateDialog'

describe('SourceCreateDialog', () => {
    it('preserves spaces while entering source metadata', async () => {
        const user = userEvent.setup()
        renderApp(
            <SourceCreateDialog
                initialValues={{ title: '', author: null, publisher: null, year: null, language: 'bg' }}
                onClose={vi.fn()}
                onCreated={vi.fn()}
            />,
        )

        const author = screen.getByRole('textbox', { name: 'Author' })
        await user.type(author, 'Ivan Petrov')

        expect(author).toHaveValue('Ivan Petrov')
    })
})
