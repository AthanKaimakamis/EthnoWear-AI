import type { EntityCardDetails } from '../../../types/catalogue'
import type { ReferenceResource } from '../../../types/reference'

export type CatalogueCategory = {
    category: ReferenceResource
    items: ReferenceResource[]
}

export function buildCatalogueCategories(
    items: EntityCardDetails[],
    uncategorizedLabel: string,
): CatalogueCategory[] {
    const sections = new Map<string, CatalogueCategory>()
    const uncategorized: ReferenceResource[] = []

    items.forEach(item => {
        const resource: ReferenceResource = {
            iri: item.iri,
            localName: item.localName,
            label: item.label,
            comment: item.comment ?? undefined,
        }

        if (item.categories.length === 0) {
            uncategorized.push(resource)
            return
        }

        item.categories.forEach(category => {
            const section = sections.get(category.localName) ?? {
                category: {
                    iri: category.iri,
                    localName: category.localName,
                    label: category.label,
                },
                items: [],
            }
            section.items.push(resource)
            sections.set(category.localName, section)
        })
    })

    const result = [...sections.values()]
    if (uncategorized.length > 0) {
        result.push({
            category: {
                iri: '#Uncategorized',
                localName: 'Uncategorized',
                label: uncategorizedLabel,
            },
            items: uncategorized,
        })
    }

    return result
}
