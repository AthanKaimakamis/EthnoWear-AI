import type { OntologyEntity } from '../../types/ontologyAdmin'

export type OntologyProfileFilter = 'all' | 'complete' | 'missing-labels' | 'missing-descriptions'
export type OntologyRelationshipFilter = 'all' | 'linked' | 'unlinked'

export function ontologyRelations(entity: OntologyEntity) {
    return [
        entity.regionGroupLocalName,
        entity.regionLocalName,
        ...(entity.characteristicRegionLocalNames ?? []),
        ...(entity.ornamentLocalNames ?? []),
        ...(entity.techniqueLocalNames ?? []),
        ...(entity.motifLocalNames ?? []),
    ].filter((value): value is string => Boolean(value))
}

export function ontologyProfileScore(entity: OntologyEntity) {
    return [entity.labelBg, entity.labelEn, entity.commentBg, entity.commentEn]
        .filter(value => Boolean(value?.trim())).length
}

export function filterOntologyEntities(
    entities: OntologyEntity[],
    category: string,
    profile: OntologyProfileFilter,
    relationship: OntologyRelationshipFilter,
) {
    return entities.filter(entity => {
        if (category && !entity.typeLocalNames?.includes(category)) return false
        const score = ontologyProfileScore(entity)
        if (profile === 'complete' && score !== 4) return false
        if (profile === 'missing-labels' && entity.labelBg?.trim() && entity.labelEn?.trim()) return false
        if (profile === 'missing-descriptions' && entity.commentBg?.trim() && entity.commentEn?.trim()) return false
        const relationCount = ontologyRelations(entity).length
        if (relationship === 'linked' && relationCount === 0) return false
        if (relationship === 'unlinked' && relationCount > 0) return false
        return true
    })
}
