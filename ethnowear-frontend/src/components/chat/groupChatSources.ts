import type { ConversationSource } from '../../api/ConversationApi'

export function groupChatSources(sources: ConversationSource[]) {
 const groups = new Map<string, { source: ConversationSource; referenceNumbers: number[]; citationIds: string[] }>()
 sources.forEach((source, index) => {
  // Missing provenance is not proof that identically titled documents are the same.
  const key = source.sourceId != null ? `source:${source.sourceId}` : `citation:${source.citationId}`
  const group = groups.get(key)
  if (group) {
   group.referenceNumbers.push(index + 1)
   if (!group.citationIds.includes(source.citationId)) group.citationIds.push(source.citationId)
  } else groups.set(key, { source, referenceNumbers: [index + 1], citationIds: [source.citationId] })
 })
 return [...groups.values()]
}
