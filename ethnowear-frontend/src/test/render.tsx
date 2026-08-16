import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router'
import { lightTheme } from '../app/theme'

export function renderApp(ui: ReactElement) {
    return render(
        <ThemeProvider theme={lightTheme}>
            <MemoryRouter>{ui}</MemoryRouter>
        </ThemeProvider>,
    )
}
