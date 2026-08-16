import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeAll } from 'vitest'
import '../app/i18n'
import i18n from '../app/i18n'

beforeAll(async () => {
    await i18n.changeLanguage('en')
})

afterEach(cleanup)
