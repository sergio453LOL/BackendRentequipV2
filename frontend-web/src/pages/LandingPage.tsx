import { motion, type Variants } from 'framer-motion'
import {
  ArrowRight,
  BarChart3,
  CalendarCheck,
  ClipboardCheck,
  CreditCard,
  FileDown,
  MapPin,
  Search,
  Sparkles,
  Upload,
} from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BrandMark } from '../components/common/BrandMark'
import { EquipmentImage } from '../components/equipment/EquipmentImage'
import { AnimatedNumber } from '../components/landing/AnimatedNumber'
import { TypedWord } from '../components/landing/TypedWord'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { buildPath, PATHS } from '../routes/paths'
import { ENDPOINTS } from '../services/endpoints'
import type { EquipmentCategory, EquipmentSummary, PageResponse } from '../types'
import { formatCurrency, formatNumber } from '../utils/format'
import { MODE_HOME, readStoredMode } from '../utils/workspaceMode'

const HERO_WORDS = ['excavadoras', 'grúas', 'rodillos', 'cargadores']
const EASE_OUT: [number, number, number, number] = [0.16, 1, 0.3, 1]

const stagger: Variants = { hidden: {}, show: { transition: { staggerChildren: 0.09, delayChildren: 0.1 } } }
const fadeUp: Variants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { duration: 0.7, ease: EASE_OUT } },
}

const integer = (value: number) => formatNumber(Math.round(value))

function CtaLink({ to, children, variant = 'action' }: { to: string; children: React.ReactNode; variant?: 'action' | 'ghost' }) {
  return (
    <motion.span whileHover={{ y: -1 }} whileTap={{ scale: 0.97 }} className="inline-flex">
      <Link
        to={to}
        className={
          variant === 'action'
            ? 'group relative inline-flex h-11 items-center gap-2 overflow-hidden rounded-lg bg-action px-5 text-sm font-semibold text-white shadow-[0_8px_30px_-8px_rgba(234,88,12,0.65)] transition-colors hover:bg-action-bright'
            : 'inline-flex h-11 items-center gap-2 rounded-lg border border-white/15 bg-white/5 px-5 text-sm font-semibold text-white transition-colors hover:border-white/30 hover:bg-white/10'
        }
      >
        {variant === 'action' && (
          // A light sweep that crosses the button on hover.
          <span className="pointer-events-none absolute inset-y-0 -left-1/2 w-1/2 -skew-x-12 bg-white/25 opacity-0 transition-all duration-700 group-hover:left-full group-hover:opacity-100" />
        )}
        {children}
      </Link>
    </motion.span>
  )
}

/** Search box with a slowly turning amber/orange border light. */
function HeroSearch({ categories }: { categories: EquipmentCategory[] }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = query.trim()
    navigate(text ? `${PATHS.catalog}?q=${encodeURIComponent(text)}` : PATHS.catalog)
  }

  return (
    <motion.div variants={fadeUp} className="w-full max-w-2xl">
      <div className="relative overflow-hidden rounded-2xl p-[1.5px] shadow-[0_0_60px_-12px_rgba(245,158,11,0.55)]">
        <div
          aria-hidden="true"
          className="absolute -inset-[150%] animate-[spin_7s_linear_infinite] bg-[conic-gradient(from_0deg,transparent_0deg,#f59e0b_70deg,#ea580c_130deg,transparent_200deg)] motion-reduce:animate-none"
        />
        <form role="search" onSubmit={handleSubmit} className="relative flex items-center gap-2 rounded-[15px] bg-slate-900/95 p-2 backdrop-blur">
          <Search className="ml-3 size-5 shrink-0 text-amber-400" aria-hidden="true" />
          <label htmlFor="hero-search" className="sr-only">
            Busca maquinaria
          </label>
          <input
            id="hero-search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="¿Qué necesitas? Excavadora CAT, grúa, rodillo…"
            autoComplete="off"
            className="h-11 min-w-0 flex-1 bg-transparent text-base text-white placeholder:text-slate-400 focus:outline-none"
          />
          <motion.button
            type="submit"
            whileTap={{ scale: 0.96 }}
            className="h-11 shrink-0 rounded-xl bg-brand px-5 text-sm font-bold text-ink transition-colors hover:bg-amber-400"
          >
            Buscar
          </motion.button>
        </form>
      </div>

      {categories.length > 0 && (
        <motion.ul variants={stagger} initial="hidden" animate="show" className="mt-4 flex flex-wrap gap-2" aria-label="Filtro rápido por categoría">
          {categories.map((category) => (
            <motion.li key={category.id} variants={fadeUp}>
              <motion.span whileHover={{ y: -2 }} whileTap={{ scale: 0.96 }} className="inline-flex">
                <Link
                  to={`${PATHS.catalog}?categoryId=${category.id}`}
                  className="inline-flex h-8 items-center rounded-full border border-white/10 bg-white/5 px-3.5 text-sm font-medium text-slate-200 transition-colors hover:border-amber-400/60 hover:bg-amber-400/10 hover:text-amber-200"
                >
                  {category.name}
                </Link>
              </motion.span>
            </motion.li>
          ))}
        </motion.ul>
      )}
    </motion.div>
  )
}

export default function LandingPage() {
  const { isAuthenticated } = useAuth()
  const catalog = useFetch<PageResponse<EquipmentSummary>>(ENDPOINTS.equipmentSearch, { page: 0, size: 100 })
  const categories = useFetch<EquipmentCategory[]>(ENDPOINTS.categories)

  const machines = catalog.data?.content ?? []
  const stats = catalog.data
    ? [
        { label: 'Máquinas publicadas', value: catalog.data.totalElements, format: integer },
        { label: 'Empresas arrendadoras', value: new Set(machines.map((machine) => machine.ownerCompanyId)).size, format: integer },
        { label: 'Ciudades con equipos', value: new Set(machines.map((machine) => machine.city)).size, format: integer },
        {
          label: 'Tarifa diaria desde',
          value: machines.length > 0 ? Math.min(...machines.map((machine) => machine.dailyRate)) : 0,
          format: (value: number) => formatCurrency(Math.round(value), machines[0]?.currency ?? 'PEN').replace(/\.00$/, ''),
        },
      ]
    : []
  const featured = machines.slice(0, 6)
  const appHome = MODE_HOME[readStoredMode()]

  return (
    <div className="min-h-screen bg-slate-50">
      {/* ---------------------------------------------------------------- hero */}
      <section className="relative isolate overflow-hidden bg-gradient-to-b from-slate-900 to-slate-800 text-white">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10">
          <div className="absolute -top-40 left-1/2 h-[520px] w-[820px] -translate-x-1/2 rounded-full bg-amber-500/25 blur-[120px]" />
          <div className="absolute top-40 -right-32 h-[380px] w-[480px] rounded-full bg-orange-600/20 blur-[110px]" />
          <div className="absolute -bottom-24 -left-24 h-[300px] w-[420px] rounded-full bg-amber-400/10 blur-[100px]" />
          <div className="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.04)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.04)_1px,transparent_1px)] bg-[size:56px_56px] [mask-image:radial-gradient(ellipse_at_center,black_30%,transparent_75%)]" />
        </div>

        <header className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <Link to={PATHS.landing} className="flex items-center gap-2.5" aria-label="RentEquip, inicio">
            <BrandMark className="size-7 text-brand" />
            <span className="text-lg font-bold tracking-tight">RentEquip</span>
          </Link>
          <nav className="hidden items-center gap-6 text-sm font-medium text-slate-300 md:flex" aria-label="Secciones">
            <a href="#maquinaria" className="transition-colors hover:text-white">
              Maquinaria
            </a>
            <a href="#como-funciona" className="transition-colors hover:text-white">
              Cómo funciona
            </a>
          </nav>
          <div className="ml-auto flex items-center gap-2">
            {isAuthenticated ? (
              <CtaLink to={appHome}>
                Ir a mi panel <ArrowRight className="size-4" aria-hidden="true" />
              </CtaLink>
            ) : (
              <>
                <Link to={PATHS.login} className="hidden h-10 items-center rounded-lg px-4 text-sm font-semibold text-slate-200 transition-colors hover:text-white sm:inline-flex">
                  Iniciar sesión
                </Link>
                <CtaLink to={PATHS.register}>Crear cuenta</CtaLink>
              </>
            )}
          </div>
        </header>

        <motion.div
          variants={stagger}
          initial="hidden"
          animate="show"
          className="mx-auto flex max-w-6xl flex-col items-center px-4 pt-16 pb-20 text-center sm:px-6 sm:pt-24 sm:pb-28"
        >
          <motion.p
            variants={fadeUp}
            className="mb-6 inline-flex items-center gap-2 rounded-full border border-amber-400/30 bg-amber-400/10 px-3.5 py-1 text-sm font-medium text-amber-200"
          >
            <Sparkles className="size-4" aria-hidden="true" /> Marketplace B2B de maquinaria pesada en el Perú
          </motion.p>
          <motion.h1 variants={fadeUp} className="max-w-4xl text-4xl leading-[1.08] font-bold tracking-tight text-balance sm:text-6xl">
            Alquila{' '}
            <TypedWord words={HERO_WORDS} className="bg-gradient-to-r from-amber-300 via-amber-400 to-orange-500 bg-clip-text text-transparent" />
            <br className="hidden sm:block" /> entre constructoras, por días.
          </motion.h1>
          <motion.p variants={fadeUp} className="mt-6 max-w-2xl text-lg text-slate-300">
            Encuentra la máquina cerca de tu obra, revisa su calendario, cotiza y paga en línea. Si tu flota está parada, publícala y
            empieza a recibir solicitudes.
          </motion.p>

          <div className="mt-10 flex w-full justify-center">
            <HeroSearch categories={categories.data ?? []} />
          </div>

          <motion.dl variants={fadeUp} className="mt-14 grid w-full max-w-4xl grid-cols-2 gap-px overflow-hidden rounded-2xl border border-white/10 bg-white/10 sm:grid-cols-4">
            {(stats.length > 0 ? stats : [0, 1, 2, 3]).map((stat, index) =>
              typeof stat === 'number' ? (
                <div key={index} className="h-[92px] bg-slate-900/80" aria-hidden="true" />
              ) : (
                <div key={stat.label} className="bg-slate-900/80 px-5 py-5 text-left">
                  <dd className="text-2xl font-bold text-white sm:text-3xl">
                    <AnimatedNumber value={stat.value} format={stat.format} />
                  </dd>
                  <dt className="mt-1 text-sm text-slate-400">{stat.label}</dt>
                </div>
              ),
            )}
          </motion.dl>
          <motion.p variants={fadeUp} className="mt-3 text-xs text-slate-500">
            Datos en vivo del catálogo de RentEquip.
          </motion.p>
        </motion.div>
      </section>

      {/* --------------------------------------------------------- machines */}
      <section id="maquinaria" className="mx-auto max-w-6xl scroll-mt-8 px-4 py-20 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-3xl font-bold tracking-tight text-ink">Disponible para tu próxima obra</h2>
            <p className="mt-2 text-slate-600">Maquinaria publicada por empresas de Lima, Callao y Arequipa.</p>
          </div>
          <Link to={PATHS.catalog} className="group inline-flex items-center gap-1.5 text-sm font-semibold text-accent-ink">
            Ver todo el catálogo <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
          </Link>
        </div>

        <ul className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {(featured.length > 0 ? featured : []).map((machine, index) => (
            <motion.li
              key={machine.id}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-60px' }}
              transition={{ duration: 0.6, delay: (index % 3) * 0.08, ease: EASE_OUT }}
            >
              <Link
                to={buildPath.equipmentDetail(machine.id)}
                className="group block overflow-hidden rounded-2xl border border-slate-200/80 bg-white transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl"
              >
                <div className="overflow-hidden">
                  <EquipmentImage
                    src={machine.primaryImageUrl}
                    alt={machine.name}
                    className="aspect-[4/3] w-full transition-transform duration-500 ease-out group-hover:scale-110"
                  />
                </div>
                <div className="flex items-end justify-between gap-3 p-5">
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-ink">{machine.name}</p>
                    <p className="mt-1 flex items-center gap-1 text-sm text-slate-500">
                      <MapPin className="size-3.5" aria-hidden="true" /> {machine.city}
                    </p>
                  </div>
                  <p className="num shrink-0 text-right text-lg font-bold text-ink">
                    {formatCurrency(machine.dailyRate, machine.currency)}
                    <span className="block text-xs font-medium text-slate-500">por día</span>
                  </p>
                </div>
              </Link>
            </motion.li>
          ))}
        </ul>
        {catalog.error && <p className="mt-6 text-sm text-slate-600">No pudimos cargar el catálogo en este momento.</p>}
      </section>

      {/* ----------------------------------------------------- how it works */}
      <section id="como-funciona" className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl scroll-mt-8 px-4 py-20 sm:px-6">
          <h2 className="text-3xl font-bold tracking-tight text-ink">Una cuenta, dos perspectivas</h2>
          <p className="mt-2 max-w-2xl text-slate-600">
            Cambia entre arrendatario y arrendador desde la cabecera: la misma empresa alquila lo que le falta y renta lo que tiene parado.
          </p>

          <div className="mt-10 grid gap-6 lg:grid-cols-2">
            {[
              {
                title: 'Arrendatario',
                subtitle: 'Comprador',
                steps: [
                  { icon: Search, text: 'Explora el catálogo y el mapa de equipos cerca de tu obra.' },
                  { icon: CalendarCheck, text: 'Revisa el calendario y cotiza los días en tiempo real.' },
                  { icon: CreditCard, text: 'Paga en línea con la garantía retenida en tu tarjeta.' },
                  { icon: FileDown, text: 'Sigue la reserva hasta la devolución y descarga el comprobante.' },
                ],
              },
              {
                title: 'Arrendador',
                subtitle: 'Vendedor',
                steps: [
                  { icon: Upload, text: 'Publica tu maquinaria con fotos y tarifas en minutos.' },
                  { icon: ClipboardCheck, text: 'Aprueba solicitudes con un clic desde tu panel.' },
                  { icon: BarChart3, text: 'Mide ingresos del mes y la ocupación de tu flota.' },
                  { icon: CalendarCheck, text: 'Coordina entregas y devoluciones en una línea de tiempo.' },
                ],
              },
            ].map((side, sideIndex) => (
              <motion.article
                key={side.title}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, margin: '-60px' }}
                transition={{ duration: 0.6, delay: sideIndex * 0.1, ease: EASE_OUT }}
                className="rounded-2xl border border-slate-200/80 bg-slate-50 p-6 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-xl sm:p-8"
              >
                <p className="text-sm font-semibold text-accent-ink">{side.subtitle}</p>
                <h3 className="mt-1 text-2xl font-bold text-ink">{side.title}</h3>
                <ol className="mt-6 space-y-4">
                  {side.steps.map(({ icon: Icon, text }) => (
                    <li key={text} className="flex items-start gap-3">
                      <Icon className="mt-0.5 size-5 shrink-0 text-brand-strong" aria-hidden="true" />
                      <span className="text-slate-700">{text}</span>
                    </li>
                  ))}
                </ol>
              </motion.article>
            ))}
          </div>
        </div>
      </section>

      {/* --------------------------------------------------------------- CTA */}
      <section className="relative isolate overflow-hidden bg-slate-900 text-white">
        <div aria-hidden="true" className="absolute -top-24 left-1/3 -z-10 h-72 w-[600px] rounded-full bg-amber-500/20 blur-[110px]" />
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-16 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2 className="text-2xl font-bold sm:text-3xl">¿Tu flota está parada?</h2>
            <p className="mt-2 text-slate-300">Registra tu empresa y publica tu primera máquina hoy.</p>
          </div>
          <CtaLink to={isAuthenticated ? PATHS.publishEquipment : PATHS.register}>
            {isAuthenticated ? 'Publicar maquinaria' : 'Crear cuenta gratis'} <ArrowRight className="size-4" aria-hidden="true" />
          </CtaLink>
        </div>
        <footer className="border-t border-white/10">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-6 text-xs text-slate-500 sm:px-6">
            <span>© 2026 RentEquip · Proyecto DBP 2026-2</span>
            <span>Lima, Perú</span>
          </div>
        </footer>
      </section>
    </div>
  )
}

