import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Snackbar, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import {
    archiveItemFeaturesApi, archiveItemMediaApi, archiveItemsApi, knowledgeChunksApi,
    mediaAssetsApi, mediaFeatureAnnotationsApi, sourceReferencesApi, sourcesApi,
} from '../../api/ArchiveAdminApi'
import { apiErrorMessage } from '../../api/http'
import { apiEnumLabel } from '../../app/apiEnumLabels'
import { invalidatePublicQueries } from '../../app/queryClient'
import { getFullReference } from '../../api/ReferenceApi'
import ArchiveRecordDialog, {
    type ArchiveField, type ArchiveFieldOption, type ArchiveFormValues,
} from '../../components/admin/ArchiveRecordDialog'
import ConfirmDialog from '../../components/admin/ConfirmDialog'
import AdminDataTable, { type AdminTableColumn } from '../../components/admin/table/AdminDataTable'
import AdminPageHeader from '../../components/admin/AdminPageHeader'
import type { PageResponse } from '../../types/api'
import type {
    ArchiveItemDetails, ArchiveItemFeatureDetails, ArchiveItemMediaDetails,
    MediaAssetDetails,
    SourceDetails, SourceReferenceDetails,
} from '../../types/archive'
import { ARCHIVE_TYPES } from '../../types/archive'
import {
    archiveAdminApi, type ArchiveAdminApi, type ArchiveAdminRecord,
    type ArchiveAdminResource, type ArchiveAdminWriteDto,
} from '../../types/archiveAdmin'
import type { ReferenceData, ReferenceResource } from '../../types/reference'

const validResources = new Set<ArchiveAdminResource>([
    'archive-items', 'sources', 'source-references', 'archive-item-features',
    'media-assets', 'archive-item-media', 'media-feature-annotations', 'knowledge-chunks',
])

const apis: Record<ArchiveAdminResource, ArchiveAdminApi> = {
    'archive-items': archiveAdminApi(archiveItemsApi),
    'sources': archiveAdminApi(sourcesApi),
    'source-references': archiveAdminApi(sourceReferencesApi),
    'archive-item-features': archiveAdminApi(archiveItemFeaturesApi),
    'media-assets': archiveAdminApi(mediaAssetsApi),
    'archive-item-media': archiveAdminApi(archiveItemMediaApi),
    'media-feature-annotations': archiveAdminApi(mediaFeatureAnnotationsApi),
    'knowledge-chunks': archiveAdminApi(knowledgeChunksApi),
}

type Lookups = {
    reference: ReferenceData | null
    sources: SourceDetails[]
    sourceReferences: SourceReferenceDetails[]
    archiveItems: ArchiveItemDetails[]
    features: ArchiveItemFeatureDetails[]
    mediaAssets: MediaAssetDetails[]
    itemMedia: ArchiveItemMediaDetails[]
}

type Column = {
    key: string
    label: string
    value: (record: ArchiveAdminRecord) => string | number | boolean | null | undefined
    enumGroup?: string
}

type ResourceDefinition = {
    fields: ArchiveField[]
    columns: Column[]
}

const emptyLookups: Lookups = {
    reference: null, sources: [], sourceReferences: [], archiveItems: [],
    features: [], mediaAssets: [], itemMedia: [],
}

const archiveTypes = [...ARCHIVE_TYPES]
const trustedLevels = ['VERIFIED', 'LIKELY', 'UNVERIFIED']
const sourceTypes = ['BOOK', 'SCANNED_BOOK', 'WEBSITE', 'MUSEUM_CATALOG', 'ARTICLE', 'FIELD_NOTE']
const mediaTypes = ['IMAGE', 'PDF', 'THUMBNAIL', 'SCAN', 'OTHER']
const mediaRoles = ['PRIMARY', 'DETAIL', 'SOURCE_SCAN', 'THUMBNAIL', 'OTHER']
const featureTypes = ['ORNAMENT', 'COLOR', 'TECHNIQUE', 'MOTIF']
const annotationTypes = ['VISIBLE_IN_IMAGE', 'PRIMARY_SUBJECT', 'DETAIL_VIEW', 'CROP_REGION']
const chunkTypes = ['GENERAL', 'REGION', 'ORNAMENT', 'TECHNIQUE', 'MOTIF', 'COLOR', 'REGIONAL_EMBROIDERY', 'SOURCE_EXCERPT']

function enumOptions(values: string[], group: string, label: (group: string, value: string) => string): ArchiveFieldOption[] {
    return values.map(value => ({ value, label: label(group, value) }))
}

function ontologyOptions(resources: ReferenceResource[] | undefined): ArchiveFieldOption[] {
    return (resources ?? []).map(resource => ({
        value: resource.localName,
        label: `${resource.label} (${resource.localName})`,
        iri: resource.iri,
    }))
}

function recordOptions<T extends { id: number }>(records: T[], label: (record: T) => string): ArchiveFieldOption[] {
    return records.map(record => ({ value: String(record.id), label: `#${record.id} - ${label(record)}` }))
}

function archiveTitle(item: ArchiveItemDetails) {
    return item.titleBg ?? item.titleEn ?? item.inventoryNumber ?? item.archiveType
}

function referenceLabel(reference: SourceReferenceDetails, lookups: Lookups) {
    const source = lookups.sources.find(item => item.id === reference.sourceId)
    const location = reference.pageFrom ? `p. ${reference.pageFrom}${reference.pageTo ? `-${reference.pageTo}` : ''}` : reference.locator
    return [source?.title ?? `Source #${reference.sourceId}`, location].filter(Boolean).join(' - ')
}

function featureOntologyOptions(values: ArchiveFormValues, reference: ReferenceData | null) {
    const resources = values.featureType === 'ORNAMENT' ? reference?.ornaments
        : values.featureType === 'COLOR' ? reference?.colors
            : values.featureType === 'TECHNIQUE' ? reference?.techniques
                : values.featureType === 'MOTIF' ? reference?.motifs
                    : values.featureType === 'REGION' ? reference?.regions
                        : values.featureType === 'REGIONAL_EMBROIDERY' ? reference?.regionalEmbroideryTypes
                            : []
    return ontologyOptions(resources)
}

function chunkOntologyOptions(values: ArchiveFormValues, reference: ReferenceData | null) {
    return featureOntologyOptions({ featureType: values.chunkType === 'SOURCE_EXCERPT' || values.chunkType === 'GENERAL' ? '' : values.chunkType }, reference)
}

function definition(resource: ArchiveAdminResource, lookups: Lookups, label: (key: string) => string, enumLabel: (group: string, value: string) => string): ResourceDefinition {
    const identity = label('sections.identity')
    const details = label('sections.details')
    const links = label('sections.links')
    const technical = label('sections.technical')
    const field = (name: string, section: string, overrides: Partial<ArchiveField> = {}): ArchiveField => ({
        name, label: label(`fields.${name}`), section, nullable: true, ...overrides,
    })
    const column = (key: string, accessor?: Column['value'], enumGroup?: string): Column => ({
        key, label: label(`fields.${key}`), value: accessor ?? (record => (record as unknown as Record<string, string | number | boolean | null>)[key]), enumGroup,
    })
    const sourceOptions = recordOptions(lookups.sources, item => item.title)
    const sourceReferenceOptions = recordOptions(lookups.sourceReferences, item => referenceLabel(item, lookups))
    const archiveItemOptions = recordOptions(lookups.archiveItems, archiveTitle)
    const featureOptions = recordOptions(lookups.features, item => `${item.featureType}: ${item.ontologyLocalName}`)
    const mediaAssetOptions = recordOptions(lookups.mediaAssets, item => item.fileName ?? item.storageUrl ?? item.mediaType)
    const itemMediaOptions = recordOptions(lookups.itemMedia, item => `${item.role} - item #${item.archiveItemId} / media #${item.mediaAssetId}`)

    if (resource === 'sources') return {
        fields: [
            field('title', identity, { required: true, nullable: false }), field('author', identity), field('publisher', identity),
            field('year', identity, { kind: 'number', min: 1 }), field('sourceType', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(sourceTypes, 'sourceType', enumLabel) }),
            field('language', details), field('isbn', details), field('url', details, { kind: 'url' }), field('filePath', details),
            field('notes', details, { kind: 'textarea' }), field('trusted', details, { kind: 'boolean', nullable: false }),
        ],
        columns: [column('title'), column('author'), column('year'), column('sourceType', undefined, 'sourceType'), column('trusted')],
    }
    if (resource === 'source-references') return {
        fields: [
            field('sourceId', links, { kind: 'select', required: true, nullable: false, options: sourceOptions }),
            field('chapter', details), field('sectionTitle', details), field('pageFrom', details, { kind: 'number', min: 1 }),
            field('pageTo', details, { kind: 'number', min: 1 }), field('figureNumber', details), field('catalogNumber', details),
            field('locator', details), field('referenceUrl', details, { kind: 'url' }), field('accessedDate', details, { kind: 'date' }),
            field('note', details, { kind: 'textarea' }),
        ],
        columns: [column('sourceId', record => lookups.sources.find(item => item.id === (record as SourceReferenceDetails).sourceId)?.title ?? (record as SourceReferenceDetails).sourceId), column('chapter'), column('pageFrom'), column('locator')],
    }
    if (resource === 'archive-items') return {
        fields: [
            field('sourceReferenceId', links, { kind: 'select', required: true, nullable: false, options: sourceReferenceOptions }),
            field('titleBg', identity), field('titleEn', identity), field('collectionId', identity), field('inventoryNumber', identity),
            field('archiveType', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(archiveTypes, 'archiveType', enumLabel) }),
            field('trustedLevel', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(trustedLevels, 'trustedLevel', enumLabel) }),
            field('descriptionBg', details, { kind: 'textarea' }), field('descriptionEn', details, { kind: 'textarea' }),
            field('periodText', details), field('originText', details), field('currentLocation', details),
            field('ontologyRegionLocalName', links, { kind: 'select', options: ontologyOptions(lookups.reference?.regions), pairedIriField: 'ontologyRegionIri' }),
            field('ontologyRegionIri', links, { kind: 'hidden' }),
            field('ontologyRegionalEmbroideryLocalName', links, { kind: 'select', options: ontologyOptions(lookups.reference?.regionalEmbroideryTypes), pairedIriField: 'ontologyRegionalEmbroideryIri' }),
            field('ontologyRegionalEmbroideryIri', links, { kind: 'hidden' }),
        ],
        columns: [column('titleBg', record => archiveTitle(record as ArchiveItemDetails)), column('archiveType', undefined, 'archiveType'), column('trustedLevel', undefined, 'trustedLevel'), column('inventoryNumber')],
    }
    if (resource === 'archive-item-features') return {
        fields: [
            field('archiveItemId', links, { kind: 'select', required: true, nullable: false, options: archiveItemOptions }),
            field('featureType', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(featureTypes, 'featureType', enumLabel), clearFields: ['ontologyLocalName', 'ontologyIri'] }),
            field('ontologyLocalName', identity, { kind: 'select', required: true, nullable: false, options: values => featureOntologyOptions(values, lookups.reference), pairedIriField: 'ontologyIri' }),
            field('ontologyIri', technical, { kind: 'hidden', required: true, nullable: false }),
            field('confidence', details, { kind: 'number', min: 0, max: 1, step: 0.01 }),
            field('validated', details, { kind: 'boolean', nullable: false }), field('sourceReferenceId', links, { kind: 'select', options: sourceReferenceOptions }),
            field('notes', details, { kind: 'textarea' }),
        ],
        columns: [column('archiveItemId'), column('featureType', undefined, 'featureType'), column('ontologyLocalName'), column('validated')],
    }
    if (resource === 'media-assets') return {
        fields: [
            field('sourceReferenceId', links, { kind: 'select', options: sourceReferenceOptions }),
            field('fileName', identity), field('mediaType', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(mediaTypes, 'mediaType', enumLabel) }),
            field('mimeType', identity), field('storageUrl', details, { kind: 'url' }), field('filePath', details),
            field('width', technical, { kind: 'number', min: 1 }), field('height', technical, { kind: 'number', min: 1 }),
            field('sizeBytes', technical, { kind: 'number', min: 0 }), field('checksum', technical),
        ],
        columns: [column('fileName'), column('mediaType', undefined, 'mediaType'), column('mimeType'), column('storageUrl')],
    }
    if (resource === 'archive-item-media') return {
        fields: [
            field('archiveItemId', links, { kind: 'select', required: true, nullable: false, options: archiveItemOptions }),
            field('mediaAssetId', links, { kind: 'select', required: true, nullable: false, options: mediaAssetOptions }),
            field('role', details, { kind: 'select', required: true, nullable: false, options: enumOptions(mediaRoles, 'mediaRole', enumLabel) }),
            field('captionBg', details, { kind: 'textarea' }), field('captionEn', details, { kind: 'textarea' }),
        ],
        columns: [column('archiveItemId'), column('mediaAssetId'), column('role', undefined, 'mediaRole'), column('captionBg')],
    }
    if (resource === 'media-feature-annotations') return {
        fields: [
            field('archiveItemMediaId', links, { kind: 'select', required: true, nullable: false, options: itemMediaOptions }),
            field('archiveItemFeatureId', links, { kind: 'select', required: true, nullable: false, options: featureOptions }),
            field('annotationType', details, { kind: 'select', required: true, nullable: false, options: enumOptions(annotationTypes, 'annotationType', enumLabel) }),
            field('x', technical, { kind: 'number', min: 0, max: 1, step: 0.000001 }), field('y', technical, { kind: 'number', min: 0, max: 1, step: 0.000001 }),
            field('width', technical, { kind: 'number', min: 0.000001, max: 1, step: 0.000001 }), field('height', technical, { kind: 'number', min: 0.000001, max: 1, step: 0.000001 }),
            field('note', details, { kind: 'textarea' }),
        ],
        columns: [column('archiveItemMediaId'), column('archiveItemFeatureId'), column('annotationType', undefined, 'annotationType'), column('note')],
    }
    return {
        fields: [
            field('chunkType', identity, { kind: 'select', required: true, nullable: false, options: enumOptions(chunkTypes, 'chunkType', enumLabel), clearFields: ['ontologyLocalName', 'ontologyIri'] }),
            field('language', identity, { required: true, nullable: false }),
            field('ontologyLocalName', links, { kind: 'select', options: values => chunkOntologyOptions(values, lookups.reference), pairedIriField: 'ontologyIri' }),
            field('ontologyIri', technical, { kind: 'hidden' }), field('sourceReferenceId', links, { kind: 'select', options: sourceReferenceOptions }),
            field('content', details, { kind: 'textarea', required: true, nullable: false, rows: 7 }),
            field('embeddingModel', technical), field('embeddingId', technical),
        ],
        columns: [column('chunkType', undefined, 'chunkType'), column('language'), column('ontologyLocalName'), column('content')],
    }
}

function extract<T>(result: PageResponse<T>) { return result.content }

function ArchiveAdminPage() {
    const { resource: routeResource } = useParams()
    const resource = validResources.has(routeResource as ArchiveAdminResource) ? routeResource as ArchiveAdminResource : 'archive-items'
    const { t, i18n } = useTranslation()
    const [items, setItems] = useState<ArchiveAdminRecord[]>([])
    const [lookups, setLookups] = useState<Lookups>(emptyLookups)
    const [loading, setLoading] = useState(true)
    const [editing, setEditing] = useState<ArchiveAdminRecord | null | undefined>(undefined)
    const [deleting, setDeleting] = useState<ArchiveAdminRecord | null>(null)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [notice, setNotice] = useState<string | null>(null)
    const api = apis[resource]
    const tr = useCallback((key: string) => t(`admin.archive.${key}`), [t])
    const enumTr = useCallback((group: string, value: string) => apiEnumLabel(t, group, value), [t])
    const resourceDefinition = useMemo(() => definition(resource, lookups, tr, enumTr), [enumTr, lookups, resource, tr])

    const load = useCallback(async (signal?: AbortSignal) => {
        await Promise.resolve()
        setLoading(true)
        setError(null)
        try {
            const language = i18n.resolvedLanguage === 'en' ? 'en' : 'bg'
            const [current, sources, references, archiveItems, features, assets, itemMedia, reference] = await Promise.all([
                api.findAll(signal), sourcesApi.findAll({ size: 1000 }, signal), sourceReferencesApi.findAll({ size: 1000 }, signal),
                archiveItemsApi.findAll({ size: 1000 }, signal), archiveItemFeaturesApi.findAll({ size: 1000 }, signal),
                mediaAssetsApi.findAll({ size: 1000 }, signal), archiveItemMediaApi.findAll({ size: 1000 }, signal), getFullReference(language),
            ])
            setItems(current.content)
            setLookups({
                sources: extract(sources), sourceReferences: extract(references), archiveItems: extract(archiveItems),
                features: extract(features), mediaAssets: extract(assets), itemMedia: extract(itemMedia), reference,
            })
        } catch (caught) {
            if (!(caught instanceof DOMException && caught.name === 'AbortError')) setError(apiErrorMessage(caught))
        } finally { setLoading(false) }
    }, [api, i18n.resolvedLanguage])

    useEffect(() => {
        const controller = new AbortController()
        const timeout = window.setTimeout(() => void load(controller.signal), 0)
        return () => { window.clearTimeout(timeout); controller.abort() }
    }, [load])

    const columns = useMemo<AdminTableColumn<ArchiveAdminRecord>[]>(() => [
        {
            key: 'id', label: 'ID', sortValue: item => item.id,
            render: item => <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.id}</Typography>,
        },
        ...resourceDefinition.columns.map(column => ({
            key: column.key,
            label: column.label,
            sortValue: column.value,
            cellSx: { maxWidth: column.key === 'content' ? 360 : 260 },
            render: (item: ArchiveAdminRecord) => {
                const rawValue = column.value(item)
                const value = column.enumGroup && rawValue != null ? enumTr(column.enumGroup, String(rawValue)) : String(rawValue ?? '—')
                return <Typography variant="body2" noWrap title={value}>{value}</Typography>
            },
        })),
    ], [enumTr, resourceDefinition.columns])

    async function save(input: ArchiveAdminWriteDto) {
        setSaving(true); setError(null)
        try {
            if (editing) await api.update(editing.id, input)
            else await api.create(input)
            void invalidatePublicQueries()
            setEditing(undefined); setNotice(t('admin.saved')); await load()
        } catch (caught) { setError(apiErrorMessage(caught)) } finally { setSaving(false) }
    }

    async function remove() {
        if (!deleting) return
        setSaving(true); setError(null)
        try {
            await api.remove(deleting.id)
            void invalidatePublicQueries()
            setDeleting(null); setNotice(t('admin.deleted')); await load()
        } catch (caught) { setError(apiErrorMessage(caught)); setDeleting(null) } finally { setSaving(false) }
    }

    const resourceKey = resource.replaceAll('-', '')
    const title = t(`admin.archive.resources.${resourceKey}`)

    return (
        <Stack spacing={3}>
            <AdminPageHeader title={title} description={t(`admin.archive.descriptions.${resourceKey}`)} />
            {error && editing === undefined && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}
            <AdminDataTable
                rows={items}
                columns={columns}
                loading={loading}
                getRowId={item => item.id}
                searchableText={item => Object.values(item).map(value => String(value ?? '')).join(' ')}
                searchPlaceholder={t('admin.archive.search')}
                defaultSortKey="id"
                onAdd={() => { setError(null); setEditing(null) }}
                onEdit={item => { setError(null); setEditing(item) }}
                onDelete={setDeleting}
            />
            <ArchiveRecordDialog key={`${resource}:${editing === undefined ? 'closed' : editing?.id ?? 'new'}`}
                open={editing !== undefined} title={editing ? t('admin.archive.editTitle', { resource: title }) : t('admin.archive.addTitle', { resource: title })}
                fields={resourceDefinition.fields} record={editing ?? null} saving={saving} error={editing !== undefined ? error : null}
                onClose={() => setEditing(undefined)} onSubmit={save} />
            <ConfirmDialog open={Boolean(deleting)} title={t('admin.confirmDelete')} pending={saving}
                onCancel={() => setDeleting(null)} onConfirm={remove}>
                {t('admin.confirmDeleteText', { name: `#${deleting?.id}` })}
            </ConfirmDialog>
            <Snackbar open={Boolean(notice)} autoHideDuration={3000} onClose={() => setNotice(null)} message={notice} />
        </Stack>
    )
}

export default ArchiveAdminPage
