import type { DocumentType, IndexingState, ProcessingState, ProvenanceStatus, ProvenanceTrustState, ReviewState } from '../../../types/document'

export const documentTypes: DocumentType[] = ['PDF_DOCUMENT', 'SCANNED_BOOK', 'PAGE_IMAGE_SET', 'STANDALONE_CAPTURE', 'UNKNOWN_FRAGMENT_SET']
export const provenanceStatuses: ProvenanceStatus[] = ['KNOWN_SOURCE', 'PARTIAL_SOURCE', 'UNKNOWN_SOURCE']
export const provenanceTrustStates: ProvenanceTrustState[] = ['UNKNOWN', 'UNTRUSTED', 'PARTIAL', 'TRUSTED', 'VERIFIED']
export const processingStates: ProcessingState[] = ['UPLOADED', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED']
export const reviewStates: ReviewState[] = ['NOT_READY', 'REVIEW_REQUIRED', 'IN_REVIEW', 'APPROVED', 'REJECTED']
export const indexingStates: IndexingState[] = ['NOT_ELIGIBLE', 'PENDING', 'INDEXED', 'FAILED', 'OUTDATED']
