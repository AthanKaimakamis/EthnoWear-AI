import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../../../test/render'
import type { DocumentPageWorkflowProgress } from '../../../types/document'
import PageWorkflowProgress from './PageWorkflowProgress'

const progress: DocumentPageWorkflowProgress = {
    documentId: 7,
    pageId: 42,
    completedSteps: 5,
    totalSteps: 5,
    steps: [
        { step: 'IMAGE_EXTRACTION', status: 'COMPLETED', jobId: 1, jobStatus: 'SUCCEEDED', message: null },
        { step: 'OCR', status: 'COMPLETED', jobId: 2, jobStatus: 'SUCCEEDED', message: null },
        { step: 'OCR_QUALITY_ASSESSMENT', status: 'COMPLETED', jobId: 3, jobStatus: 'SUCCEEDED', message: null },
        { step: 'VISION_OCR_ASSESSMENT', status: 'COMPLETED', jobId: 4, jobStatus: 'SUCCEEDED', message: null },
        { step: 'HUMAN_REVIEW', status: 'COMPLETED', jobId: null, jobStatus: null, message: null },
    ],
}

describe('PageWorkflowProgress', () => {
    it('excludes optional manual vision comparison from compact progress', () => {
        renderApp(<PageWorkflowProgress progress={progress} compact />)

        expect(screen.getByText('4/4')).toBeInTheDocument()
        expect(screen.queryByText('5/5')).not.toBeInTheDocument()
    })

    it('excludes optional manual vision comparison from the workflow stepper', () => {
        renderApp(<PageWorkflowProgress progress={progress} />)

        expect(screen.getByText(/4.*4/)).toBeInTheDocument()
        expect(screen.queryByText('Ръчно поискано визуално сравнение')).not.toBeInTheDocument()
    })
})
