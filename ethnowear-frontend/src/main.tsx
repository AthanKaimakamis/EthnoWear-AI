import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ThemeProvider} from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { lightTheme } from './app/theme'
import './app/globalImports'
import './app/i18n'
import './index.css'
import { router } from './app/routes'
import {RouterProvider} from "react-router";

createRoot(document.getElementById('root')!).render(
  <StrictMode>
      <ThemeProvider theme={lightTheme}>
          <CssBaseline />
          <RouterProvider router={router} />
      </ThemeProvider>
  </StrictMode>
)
