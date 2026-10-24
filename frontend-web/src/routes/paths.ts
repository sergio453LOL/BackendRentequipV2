export const PATHS = {
  landing: '/',
  home: '/panel',
  login: '/login',
  register: '/register',
  catalog: '/catalog',
  fleet: '/fleet',
  publishEquipment: '/equipment/new',
  equipmentDetail: '/equipment/:id',
  reservations: '/reservations',
} as const

export const buildPath = {
  equipmentDetail: (id: number) => `/equipment/${id}`,
}
