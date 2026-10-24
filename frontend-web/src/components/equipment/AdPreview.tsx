import { AnimatePresence, motion } from 'framer-motion'
import { Eye, ImagePlus, MapPin } from 'lucide-react'
import { formatCurrency } from '../../utils/format'
import { StatusBadge } from '../common/StatusBadge'

interface AdPreviewProps {
  name: string
  brand: string
  model: string
  year: string
  city: string
  companyName: string
  dailyRate: string
  weeklyRate: string
  securityDeposit: string
  categories: string[]
  coverUrl: string | undefined
}

const money = (value: string) => (value && Number(value) > 0 ? formatCurrency(Number(value)) : null)

/** The listing as renters will see it in the catalogue, updated as the form is filled in. */
export function AdPreview(props: AdPreviewProps) {
  const daily = money(props.dailyRate)
  const weekly = money(props.weeklyRate)
  const deposit = money(props.securityDeposit)
  const subtitle = [props.brand, props.model, props.year].filter(Boolean).join(' · ')

  return (
    <aside aria-label="Vista previa del anuncio" className="xl:sticky xl:top-24">
      <p className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-600">
        <Eye className="size-4 text-brand-strong" aria-hidden="true" /> Vista previa del anuncio
      </p>
      <motion.article layout className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-xl shadow-slate-900/5">
        <div className="relative aspect-[4/3] overflow-hidden bg-slate-100">
          <AnimatePresence mode="wait">
            {props.coverUrl ? (
              <motion.img
                key={props.coverUrl}
                src={props.coverUrl}
                alt=""
                initial={{ opacity: 0, scale: 1.04 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.35 }}
                className="size-full object-cover"
              />
            ) : (
              <motion.div key="empty" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex size-full flex-col items-center justify-center gap-2 text-slate-400">
                <ImagePlus className="size-8" aria-hidden="true" />
                <span className="text-sm font-medium">Tu foto de portada</span>
              </motion.div>
            )}
          </AnimatePresence>
          <span className="absolute top-3 left-3">
            <StatusBadge kind="equipment" status="AVAILABLE" />
          </span>
        </div>
        <div className="space-y-3 p-5">
          <div>
            <h3 className={props.name ? 'text-lg font-bold text-ink' : 'text-lg font-bold text-slate-300'}>{props.name || 'Nombre del equipo'}</h3>
            <p className="text-sm text-slate-500">{subtitle || 'Marca · Modelo · Año'}</p>
          </div>
          <p className="flex items-center gap-1.5 text-sm text-slate-600">
            <MapPin className="size-4 text-slate-400" aria-hidden="true" />
            {props.city || 'Ciudad'} · {props.companyName}
          </p>
          {props.categories.length > 0 && (
            <ul className="flex flex-wrap gap-1.5">
              {props.categories.map((category) => (
                <motion.li
                  key={category}
                  layout
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  className="rounded-full bg-accent-tint px-2.5 py-0.5 text-xs font-semibold text-amber-900"
                >
                  {category}
                </motion.li>
              ))}
            </ul>
          )}
          <div className="flex items-end justify-between gap-3 border-t border-slate-100 pt-3">
            <p className="num text-2xl font-bold text-ink">
              {daily ?? 'S/ —'}
              <span className="text-sm font-medium text-slate-500"> / día</span>
            </p>
            <div className="num text-right text-xs text-slate-500">
              {weekly && <p>{weekly} / semana</p>}
              {deposit && <p>Garantía {deposit}</p>}
            </div>
          </div>
        </div>
      </motion.article>
    </aside>
  )
}
