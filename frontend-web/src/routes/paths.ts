export const PATHS = {
  home: '/',
  login: '/login',
  register: '/register',
  catalog: '/catalog',
  publishEquipment: '/equipment/new',
  equipmentDetail: '/equipment/:id',
  reservations: '/reservations',
} as const

export const buildPath = {
  equipmentDetail: (id: number) => `/equipment/${id}`,
}
