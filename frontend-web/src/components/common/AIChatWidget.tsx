import { Bot, MessageCircle, Send, Sparkles, X } from 'lucide-react'
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

/**
 * Floating assistant. Questions go to the backend, which asks Claude to pick machines from the live
 * catalogue (or uses a local keyword recommender when no model is configured).
 */
export function AIChatWidget() {
  const [isOpen, setIsOpen] = useState(false)
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

  return (
    <div className="fixed right-4 bottom-4 z-40 flex flex-col items-end gap-3 sm:right-6 sm:bottom-6">
      {isOpen && (
        <section
          aria-label="Asistente RentEquip"
          className="flex h-[min(34rem,calc(100vh-7rem))] w-[calc(100vw-2rem)] max-w-sm animate-[slide-up_200ms_ease-out] flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
        >
          <header className="flex items-center gap-3 bg-slate-900 px-4 py-3 text-white">
            <span className="flex size-8 items-center justify-center rounded-full bg-brand-500 text-slate-900">
              <Bot className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0">
              <h2 className="text-sm font-semibold">Asistente RentEquip</h2>
              <p className="text-xs text-slate-400">Recomendaciones del catálogo con IA</p>
            </div>
            <button
              type="button"
              onClick={() => setIsOpen(false)}
              aria-label="Cerrar asistente"
              className="ml-auto rounded p-1 hover:bg-white/10"
            >
              <X className="size-4" />
            </button>
          </header>

          <div className="flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
            {messages.map((message) => (
              <div key={message.id} className={cn('flex flex-col gap-2', message.role === 'user' && 'items-end')}>
                <p
                  className={cn(
                    'max-w-[88%] rounded-2xl px-3 py-2 text-sm whitespace-pre-wrap',
                    message.role === 'user'
                      ? 'rounded-br-sm bg-brand-500 text-slate-900'
                      : 'rounded-bl-sm bg-slate-100 text-slate-800',
                    message.isError && 'bg-red-50 text-red-700',
                  )}
                >
                  {message.content}
                </p>
                {message.recommendations && message.recommendations.length > 0 && (
                  <ul className="w-full space-y-2" aria-label="Maquinaria recomendada">
                    {message.recommendations.map((equipment) => (
                      <li key={equipment.id}>
                        <RecommendationCard equipment={equipment} onOpen={() => setIsOpen(false)} />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}

            {showSuggestions && (
              <div className="space-y-2 pt-1">
                <p className="flex items-center gap-1 text-xs font-medium text-slate-500">
                  <Sparkles className="size-3.5 text-brand-500" aria-hidden="true" /> Prueba preguntando:
                </p>
                {SUGGESTIONS.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => void ask(suggestion)}
                    className="block w-full rounded-xl border border-slate-200 px-3 py-2 text-left text-sm text-slate-700 transition hover:border-brand-400 hover:bg-brand-50"
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
              className="h-10 flex-1 rounded-full border border-slate-300 px-4 text-sm focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none disabled:bg-slate-50"
            />
            <button
              type="submit"
              disabled={!draft.trim() || isReplying}
              aria-label="Enviar pregunta"
              className="flex size-10 items-center justify-center rounded-full bg-brand-500 text-slate-900 transition hover:bg-brand-400 disabled:opacity-50"
            >
              <Send className="size-4" />
            </button>
          </form>
        </section>
      )}

      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        aria-expanded={isOpen}
        aria-label={isOpen ? 'Cerrar asistente' : 'Abrir asistente de IA'}
        className="flex size-14 items-center justify-center rounded-full bg-slate-900 text-brand-400 shadow-lg transition hover:scale-105 hover:bg-slate-800"
      >
        {isOpen ? <X className="size-6" /> : <MessageCircle className="size-6" />}
      </button>
    </div>
  )
}

function RecommendationCard({ equipment, onOpen }: { equipment: EquipmentSummary; onOpen: () => void }) {
  return (
    <Link
      to={buildPath.equipmentDetail(equipment.id)}
      onClick={onOpen}
      className="flex items-center gap-3 rounded-xl border border-slate-200 bg-white p-2 transition hover:border-brand-400 hover:shadow-sm"
    >
      <EquipmentImage src={equipment.primaryImageUrl} alt={equipment.name} className="size-12 shrink-0 rounded-lg" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-slate-900">{equipment.name}</p>
        <p className="truncate text-xs text-slate-500">
          {equipment.city} · {formatCurrency(equipment.dailyRate, equipment.currency)}/día
        </p>
      </div>
    </Link>
  )
}
