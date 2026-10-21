import { LocateFixed, Send } from 'lucide-react'
import { useCallback, useState, type ReactNode } from 'react'
import { Controller, type Path } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { Button } from '../components/common/Button'
import { ChipSelect } from '../components/common/ChipSelect'
import { Input } from '../components/common/Input'
import { PageHeader } from '../components/common/PageHeader'
import { Select } from '../components/common/Select'
import { Textarea } from '../components/common/Textarea'
import { ImageUploader } from '../components/equipment/ImageUploader'
import { useAuth } from '../hooks/useAuth'
import { useFetch } from '../hooks/useFetch'
import { useMutation } from '../hooks/useMutation'
import { useZodForm } from '../hooks/useZodForm'
import { buildPath, PATHS } from '../routes/paths'
import { ENDPOINTS } from '../services/endpoints'
import { equipmentService } from '../services/equipmentService'
import type { EquipmentCategory } from '../types'
import { findCity, PERU_CITIES } from '../utils/cities'
import { applyServerFieldErrors } from '../utils/formErrors'

const MONEY = /^\d{1,10}(\.\d{1,2})?$/
const CURRENT_YEAR = new Date().getFullYear()

const optionalText = (max: number) => z.string().trim().max(max, `Máximo ${max} caracteres.`)

const publishSchema = z
  .object({
    name: z.string().trim().min(3, 'Mínimo 3 caracteres.').max(150, 'Máximo 150 caracteres.'),
    categoryIds: z.array(z.number()).min(1, 'Elige al menos una categoría.'),
    brand: z.string().trim().min(1, 'Ingresa la marca.').max(80, 'Máximo 80 caracteres.'),
    model: z.string().trim().min(1, 'Ingresa el modelo.').max(80, 'Máximo 80 caracteres.'),
    manufactureYear: z
      .string()
      .refine((value) => value === '' || (/^\d{4}$/.test(value) && Number(value) >= 1950 && Number(value) <= CURRENT_YEAR + 1), {
        message: `Año entre 1950 y ${CURRENT_YEAR + 1}.`,
      }),
    serialNumber: optionalText(100),
    description: optionalText(2000),
    specifications: optionalText(4000),
    dailyRate: z
      .string()
      .regex(MONEY, 'Monto con hasta 2 decimales, por ejemplo 850.50.')
      .refine((value) => Number(value) >= 0.01, 'La tarifa debe ser mayor a 0.'),
    weeklyRate: z.string().refine((value) => value === '' || (MONEY.test(value) && Number(value) >= 0.01), {
      message: 'Monto con hasta 2 decimales.',
    }),
    securityDeposit: z.string().regex(MONEY, 'Monto con hasta 2 decimales (puede ser 0).'),
    city: z.string().min(1, 'Selecciona la ciudad donde está el equipo.'),
    address: optionalText(255),
    latitude: z.string(),
    longitude: z.string(),
  })
  .refine((values) => values.weeklyRate === '' || Number(values.weeklyRate) <= Number(values.dailyRate) * 7, {
    path: ['weeklyRate'],
    message: 'La tarifa semanal no debería superar 7 días de tarifa diaria.',
  })

type PublishFormValues = z.infer<typeof publishSchema>

const SERVER_FIELD_MAP: Partial<Record<string, Path<PublishFormValues>>> = {
  latitude: 'city',
  longitude: 'city',
  imageUrls: 'name',
}

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <section className="grid gap-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6 lg:grid-cols-3">
      <div>
        <h2 className="font-semibold text-slate-900">{title}</h2>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
      <div className="grid gap-5 sm:grid-cols-2 lg:col-span-2">{children}</div>
    </section>
  )
}

export default function PublishEquipmentPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const categories = useFetch<EquipmentCategory[]>(ENDPOINTS.categories)
  const [imageUrls, setImageUrls] = useState<string[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [locationNote, setLocationNote] = useState<string | null>(null)

  const {
    register,
    control,
    handleSubmit,
    setValue,
    setError,
    formState: { errors },
  } = useZodForm(publishSchema, {
    defaultValues: {
      name: '',
      categoryIds: [],
      brand: '',
      model: '',
      manufactureYear: '',
      serialNumber: '',
      description: '',
      specifications: '',
      dailyRate: '',
      weeklyRate: '',
      securityDeposit: '0',
      city: '',
      address: '',
      latitude: '',
      longitude: '',
    },
  })

  const publish = useMutation(equipmentService.create, {
    successMessage: (equipment) => `"${equipment.name}" ya está publicado en el catálogo`,
    errorTitle: 'No se pudo publicar la maquinaria',
    onSuccess: (equipment) => navigate(buildPath.equipmentDetail(equipment.id), { replace: true }),
    onError: (error) => applyServerFieldErrors(error, setError, SERVER_FIELD_MAP),
  })

  const handleImagesChange = useCallback((urls: string[]) => setImageUrls(urls), [])

  function detectLocation() {
    if (!('geolocation' in navigator)) {
      setLocationNote('Tu navegador no permite detectar la ubicación; usaremos el centro de la ciudad.')
      return
    }
    setLocationNote('Detectando ubicación…')
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setValue('latitude', position.coords.latitude.toFixed(7))
        setValue('longitude', position.coords.longitude.toFixed(7))
        setLocationNote('📍 Usaremos la ubicación exacta del equipo para mostrarlo en el mapa.')
      },
      () => setLocationNote('No pudimos obtener tu ubicación; usaremos el centro de la ciudad.'),
      { timeout: 10_000 },
    )
  }

  async function onSubmit(values: PublishFormValues) {
    if (!user) return
    const city = findCity(values.city)
    const latitude = values.latitude ? Number(values.latitude) : city?.latitude
    const longitude = values.longitude ? Number(values.longitude) : city?.longitude
    if (latitude === undefined || longitude === undefined) {
      setError('city', { message: 'Selecciona una ciudad válida.' })
      return
    }
    await publish.mutate({
      name: values.name,
      categoryIds: values.categoryIds,
      brand: values.brand,
      model: values.model,
      manufactureYear: values.manufactureYear ? Number(values.manufactureYear) : undefined,
      serialNumber: values.serialNumber || undefined,
      description: values.description || undefined,
      specifications: values.specifications || undefined,
      dailyRate: Number(values.dailyRate),
      weeklyRate: values.weeklyRate ? Number(values.weeklyRate) : undefined,
      securityDeposit: Number(values.securityDeposit),
      currency: 'PEN',
      city: values.city,
      address: values.address || undefined,
      latitude,
      longitude,
      imageUrls,
      ownerCompanyId: user.companyId,
    })
  }

  const cityField = register('city')

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        title="Publicar maquinaria"
        description="Completa la ficha del equipo. Las empresas del catálogo podrán verlo y solicitar reservas."
        breadcrumbs={[{ label: 'Dashboard', to: PATHS.home }, { label: 'Publicar maquinaria' }]}
      />

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-6">
        <Section title="Datos del equipo" description="Lo que verán primero las empresas en el catálogo.">
          <Input className="sm:col-span-2" label="Nombre" required placeholder="Excavadora hidráulica CAT 320" error={errors.name?.message} {...register('name')} />
          <Input label="Marca" required placeholder="Caterpillar" error={errors.brand?.message} {...register('brand')} />
          <Input label="Modelo" required placeholder="320 GC" error={errors.model?.message} {...register('model')} />
          <Input label="Año de fabricación" inputMode="numeric" maxLength={4} placeholder={String(CURRENT_YEAR - 3)} error={errors.manufactureYear?.message} {...register('manufactureYear')} />
          <Input label="N.º de serie" placeholder="CAT0320GCXYZ" error={errors.serialNumber?.message} {...register('serialNumber')} />
          <div className="sm:col-span-2">
            <Controller
              control={control}
              name="categoryIds"
              render={({ field }) => (
                <ChipSelect
                  label="Categorías"
                  required
                  options={(categories.data ?? []).map((category) => ({ value: category.id, label: category.name }))}
                  value={field.value}
                  onChange={field.onChange}
                  error={errors.categoryIds?.message}
                  hint={
                    categories.error
                      ? 'No pudimos cargar las categorías. Recarga la página.'
                      : categories.data?.length === 0
                        ? 'Aún no hay categorías: un administrador debe crearlas primero.'
                        : undefined
                  }
                />
              )}
            />
          </div>
          <Textarea className="sm:col-span-2" label="Descripción" rows={4} placeholder="Estado del equipo, horas de uso, si incluye operador…" error={errors.description?.message} {...register('description')} />
        </Section>

        <Section title="Tarifas" description="Montos en soles (PEN). La garantía se retiene en la tarjeta del cliente y se libera al devolver el equipo.">
          <Input label="Tarifa diaria (S/)" required inputMode="decimal" placeholder="850.00" error={errors.dailyRate?.message} {...register('dailyRate')} />
          <Input label="Tarifa semanal (S/)" inputMode="decimal" placeholder="5000.00" hint="Opcional" error={errors.weeklyRate?.message} {...register('weeklyRate')} />
          <Input label="Depósito de garantía (S/)" required inputMode="decimal" placeholder="2000.00" error={errors.securityDeposit?.message} {...register('securityDeposit')} />
        </Section>

        <Section title="Especificaciones técnicas" description='Escribe una por línea como "Clave: valor"; en la ficha se mostrarán como tabla.'>
          <Textarea
            className="sm:col-span-2"
            label="Especificaciones"
            rows={6}
            placeholder={'Peso operativo: 22 t\nPotencia: 121 kW\nCapacidad del cucharón: 1.2 m³'}
            error={errors.specifications?.message}
            {...register('specifications')}
          />
        </Section>

        <Section title="Ubicación" description="Dónde se recoge o entrega el equipo. Se usa para la búsqueda por cercanía.">
          <Select
            label="Ciudad"
            required
            placeholder="Selecciona una ciudad"
            options={PERU_CITIES.map((city) => ({ value: city.name, label: city.name }))}
            error={errors.city?.message}
            {...cityField}
            onChange={(event) => {
              void cityField.onChange(event)
              setValue('latitude', '')
              setValue('longitude', '')
              setLocationNote(null)
            }}
          />
          <Input label="Dirección" placeholder="Av. Industrial 450, Ate" error={errors.address?.message} {...register('address')} />
          <div className="flex flex-col gap-2 rounded-xl bg-slate-50 p-4 text-sm sm:col-span-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-slate-600">{locationNote ?? 'Por defecto usamos el centro de la ciudad elegida.'}</p>
            <Button variant="secondary" size="sm" onClick={detectLocation} leftIcon={<LocateFixed className="size-4" />}>
              Usar mi ubicación
            </Button>
          </div>
        </Section>

        <Section title="Fotos" description="La primera foto es la portada del catálogo. Las fotos con buena luz se alquilan más.">
          <div className="sm:col-span-2">
            <ImageUploader onChange={handleImagesChange} onBusyChange={setIsUploading} />
          </div>
        </Section>

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="secondary" onClick={() => navigate(-1)} disabled={publish.isLoading}>
            Cancelar
          </Button>
          <Button type="submit" size="lg" isLoading={publish.isLoading} disabled={isUploading} leftIcon={<Send className="size-4" />}>
            {isUploading ? 'Esperando las fotos…' : 'Publicar maquinaria'}
          </Button>
        </div>
      </form>
    </div>
  )
}
