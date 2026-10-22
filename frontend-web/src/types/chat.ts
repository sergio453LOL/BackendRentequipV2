import type { EquipmentSummary } from './equipment'

export type ChatRole = 'user' | 'assistant'

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  createdAt: Date
  recommendations?: EquipmentSummary[]
  isError?: boolean
}

/** One turn of the conversation as the backend expects it. */
export interface ChatTurn {
  role: ChatRole
  content: string
}

export interface AssistantChatRequest {
  messages: ChatTurn[]
}

export interface AssistantChatResponse {
  reply: string
  recommendations: EquipmentSummary[]
  /** `claude` when the language model answered, `catalog-search` for the offline recommender. */
  source: 'claude' | 'catalog-search'
}
