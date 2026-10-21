import type { AssistantChatRequest, AssistantChatResponse, ChatTurn } from '../types'
import { api } from './api'
import { ENDPOINTS } from './endpoints'

const MAX_HISTORY_TURNS = 12
/** The language model can take a while on the first question; give it more room than a normal request. */
const ASSISTANT_TIMEOUT_MS = 60_000

export const assistantService = {
  async chat(history: ChatTurn[]): Promise<AssistantChatResponse> {
    const body: AssistantChatRequest = { messages: history.slice(-MAX_HISTORY_TURNS) }
    const { data } = await api.post<AssistantChatResponse>(ENDPOINTS.assistantChat, body, {
      timeout: ASSISTANT_TIMEOUT_MS,
    })
    return data
  },
}
