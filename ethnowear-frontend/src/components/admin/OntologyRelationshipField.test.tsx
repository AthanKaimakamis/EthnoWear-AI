import { screen, waitForElementToBeRemoved } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../../test/render'
import OntologyRelationshipField from './OntologyRelationshipField'

describe('OntologyRelationshipField', () => {
    it('commits multi-selection only when the modal is saved', async () => {
        const onChange = vi.fn()
        const user = userEvent.setup()
        renderApp(
            <OntologyRelationshipField
                label="Ornaments"
                options={[
                    { value: 'Flower', label: 'Flower (Flower)' },
                    { value: 'Cross', label: 'Cross (Cross)' },
                ]}
                value={[]}
                onChange={onChange}
            />,
        )

        await user.click(screen.getByRole('button', { name: 'Link Ornaments' }))
        await user.click(screen.getByRole('checkbox', { name: 'Flower (Flower)' }))
        await user.click(screen.getByRole('button', { name: 'Cancel' }))
        await waitForElementToBeRemoved(() => screen.queryByRole('dialog'))
        expect(onChange).not.toHaveBeenCalled()

        await user.click(screen.getByRole('button', { name: 'Link Ornaments' }))
        await user.click(screen.getByRole('checkbox', { name: 'Cross (Cross)' }))
        await user.click(screen.getByRole('button', { name: 'Save' }))
        expect(onChange).toHaveBeenCalledWith(['Cross'])
    })

    it('uses a single radio selection for one-value relationships', async () => {
        const onChange = vi.fn()
        const user = userEvent.setup()
        renderApp(
            <OntologyRelationshipField
                label="Region"
                mode="single"
                options={[
                    { value: 'East', label: 'East (East)' },
                    { value: 'West', label: 'West (West)' },
                ]}
                value={[]}
                onChange={onChange}
            />,
        )

        await user.click(screen.getByRole('button', { name: 'Choose Region' }))
        await user.click(screen.getByRole('radio', { name: 'West (West)' }))
        await user.click(screen.getByRole('button', { name: 'Save' }))
        expect(onChange).toHaveBeenCalledWith(['West'])
    })
})
