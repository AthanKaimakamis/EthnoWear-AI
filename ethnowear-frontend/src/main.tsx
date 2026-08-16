import { StrictMode, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { ThemeProvider} from '@mui/material/styles'
import CssBaseline from '@mui/material/CssBaseline'
import { lightTheme } from './app/theme'
import './app/globalImports'
import './app/i18n'
import './index.css'
import { router } from './app/routes'
import {RouterProvider} from "react-router";
import { AdminAuthProvider } from './app/AdminAuthContext'
import PageLoading from './components/loading/PageLoading'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './app/queryClient'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
      <ThemeProvider theme={lightTheme}>
          <CssBaseline />
          <QueryClientProvider client={queryClient}>
              <AdminAuthProvider>
                  <Suspense fallback={<PageLoading />}>
                      <RouterProvider router={router} />
                  </Suspense>
              </AdminAuthProvider>
          </QueryClientProvider>
      </ThemeProvider>
  </StrictMode>
)
