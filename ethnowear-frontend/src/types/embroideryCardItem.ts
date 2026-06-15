export type EmbroideryCardItem = {
    id: string
    title: string
    description: string
    imageUrl?: string
    regionLabel?: string
    regionLocalName?: string
    regionalEmbroideryLocalName?: string
    source: 'ontology' | 'archive' | 'mixed'
}