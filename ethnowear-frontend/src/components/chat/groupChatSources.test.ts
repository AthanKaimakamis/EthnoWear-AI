import { describe, expect, it } from 'vitest'
import { groupChatSources } from './groupChatSources'

describe('grouped chat sources', () => {
 it('shows one book while retaining all five passage references', () => {
  const sources = Array.from({ length: 5 }, (_, i) => ({ citationId: `chunk:${i}`, sourceId: 1, title: 'Book', author: 'Author' }))
  const result = groupChatSources(sources)
  expect(result).toHaveLength(1)
  expect(result[0].referenceNumbers).toEqual([1, 2, 3, 4, 5])
  expect(result[0].citationIds).toHaveLength(5)
  expect(sources).toHaveLength(5)
 })
 it('does not merge different sources sharing a title or unknown provenance', () => {
  expect(groupChatSources([1, 2, null, null].map((sourceId, i) => ({ citationId: `chunk:${i}`, sourceId, title: 'Same title', author: null })))).toHaveLength(4)
 })
})
