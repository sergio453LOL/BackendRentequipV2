import { Send, Sparkles, X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { buildPath } from '../../routes/paths'
import { assistantService } from '../../services/assistantService'
import type { ChatMessage, ChatRole, EquipmentSummary } from '../../types'
import { getErrorMessage } from '../../utils/apiError'
import { cn } from '../../utils/cn'
import { formatCurrency } from '../../utils/format'
import { EquipmentImage } from '../equipment/EquipmentImage'
import { Spinner } from './Spinner'

const GREETING =
  '¡Hola! Soy el asistente de RentEquip. Cuéntame qué trabajo vas a hacer y en qué ciudad, y te recomiendo maquinaria del catálogo.'

const SUGGESTIONS = [
  '¿Qué equipo me recomiendas para nivelar un terreno en Lima?',
  'Necesito excavar zanjas para tuberías',
  '¿Qué uso para compactar asfalto?',
]

const MAX_MESSAGE_LENGTH = 1000

function createMessage(role: ChatRole, content: string, extra: Partial<ChatMessage> = {}): ChatMessage {
  return { id: crypto.randomUUID(), role, content, createdAt: new Date(), ...extra }
}

interface AIChatWidgetProps {
  isOpen: boolean
  onClose: () => void
}

/**
 * Assistant panel, opened from the top bar. Questions go to the backend, which asks Claude to pick
 * machines from the live catalogue (or uses a local keyword recommender when no model is configured).
 */
export function AIChatWidget({ isOpen, onClose }: AIChatWidgetProps) {
  const [messages, setMessages] = useState<ChatMessage[]>(() => [createMessage('assistant', GREETING)])
  const [draft, setDraft] = useState('')
  const [isReplying, setIsReplying] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, isReplying, isOpen])

  useEffect(() => {
    if (isOpen) inputRef.current?.focus()
  }, [isOpen])

  async function ask(question: string) {
    const text = question.trim()
    if (!text || isReplying) return

    const userMessage = createMessage('user', text)
    // The greeting is UI copy, not part of the conversation the model should see.
    const history = [...messages.slice(1), userMessage]
      .filter((message) => !message.isError)
      .map(({ role, content }) => ({ role, content }))

    setMessages((current) => [...current, userMessage])
    setDraft('')
    setIsReplying(true)
    try {
      const response = await assistantService.chat(history)
      setMessages((current) => [
        ...current,
        createMessage('assistant', response.reply, { recommendations: response.recommendations }),
      ])
    } catch (error) {
      setMessages((current) => [...current, createMessage('assistant', getErrorMessage(error), { isError: true })])
    } finally {
      setIsReplying(false)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void ask(draft)
  }

  const showSuggestions = messages.length === 1 && !isReplying

  if (!isOpen) return null

  return (
        <section
          aria-label="Asistente RentEquip"
          className="fixed top-[72px] right-2 z-40 flex h-[min(36rem,calc(100vh-4.5rem))] w-[calc(100vw-1rem)] max-w-sm animate-[fade-in_150ms_ease-out] flex-col overflow-hidden rounded-lg border border-slate-200 bg-white shadow-[0_12px_32px_rgba(0,0,0,0.12)] sm:right-4"
        >
          <header className="flex items-center gap-3 border-b border-rule px-4 py-3">
            <div className="min-w-0">
              <h2 className="text-sm font-semibold text-ink">Asistente RentEquip</h2>
              <p className="text-xs text-slate-500">Te recomienda maquinaria del catálogo para tu trabajo</p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar asistente"
              className="ml-auto rounded-md p-1.5 text-slate-500 hover:bg-raised hover:text-ink"
            >
              <X className="size-4" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
            {messages.map((message) => (
              <div key={message.id} className={cn('flex flex-col gap-2', message.role === 'user' && 'items-end')}>
                <p
                  className={cn(
                    'max-w-[88%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap',
                    message.role === 'user'
                      ? 'bg-ink text-white'
                      : 'bg-raised text-ink',
                    message.isError && 'bg-red-50 text-red-700',
                  )}
                >
                  {message.content}
                </p>
                {message.recommendations && message.recommendations.length > 0 && (
                  <ul className="w-full space-y-2" aria-label="Maquinaria recomendada">
                    {message.recommendations.map((equipment) => (
                      <li key={equipment.id}>
                        <RecommendationCard equipment={equipment} onOpen={onClose} />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}

            {showSuggestions && (
              <div className="space-y-2 pt-1">
                <p className="flex items-center gap-1 text-xs font-medium text-slate-500">
                  <Sparkles className="size-3.5 text-accent" aria-hidden="true" /> Prueba preguntando:
                </p>
                {SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => void ask(suggestion)}
                    className="block w-full rounded-lg border border-slate-200 px-3 py-2 text-left text-sm text-slate-700 transition hover:border-ink hover:bg-raised"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            )}

            {isReplying && (
              <div className="flex items-center gap-2 text-xs text-slate-500">
                <Spinner size="sm" label="El asistente está pensando" />
                Buscando en el catálogo…
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form onSubmit={handleSubmit} className="flex items-center gap-2 border-t border-slate-200 p-3">
            <label htmlFor="ai-chat-input" className="sr-only">
              Escribe tu pregunta
            </label>
            <input
              id="ai-chat-input"
              ref={inputRef}
              value={draft}
              maxLength={MAX_MESSAGE_LENGTH}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="¿Qué máquina necesito para…?"
              autoComplete="off"
              disabled={isReplying}
              className="h-9 flex-1 rounded-md border border-slate-300 px-3 text-sm focus:border-ink focus:ring-2 focus:ring-slate-200 focus:outline-none disabled:bg-slate-50"
            />
            <button
              type="submit"
              disabled={!draft.trim() || isReplying}
              aria-label="Enviar pregunta"
              className="flex size-9 items-center justify-center rounded-md bg-ink text-white transition hover:bg-ink-hover disabled:opacity-50"
            >
              <Send className="size-4" />
            </button>
          </form>
        </section>
  )
}

function RecommendationCard({ equipment, onOpen }: { equipment: EquipmentSummary; onOpen: () => void }) {
  return (
    <Link
      to={buildPath.equipmentDetail(equipment.id)}
      onClick={onOpen}
      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-2 transition hover:border-slate-400"
    >
      <EquipmentImage src={equipment.primaryImageUrl} alt={equipment.name} className="size-12 shrink-0 rounded-md" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-900">{equipment.name}</p>
        <p className="truncate text-xs text-slate-500">
          {equipment.city} · {formatCurrency(equipment.dailyRate, equipment.currency)}/día
        </p>
      </div>
    </Link>
  )
}
