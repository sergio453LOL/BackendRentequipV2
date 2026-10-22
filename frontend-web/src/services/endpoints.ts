/** Every backend route the web app talks to, relative to `API_BASE_URL`. */
export const ENDPOINTS = {
  auth: {
    login: '/auth/login',
    register: '/auth/register',
  },
  categories: '/equipment-categories',
  equipmentSearch: '/equipment/search',
  equipment: (id: number | string) => `/equipment/${id}`,
  companyEquipment: (companyId: number) => `/companies/${companyId}/equipment`,
  myDashboard: '/companies/me/dashboard',
  reservations: '/reservations',
  reservation: (id: number) => `/reservations/${id}`,
  reservationConfirmation: (id: number) => `/reservations/${id}/confirmation`,
  reservationCancellation: (id: number) => `/reservations/${id}/cancellation`,
  reservationStatus: (id: number) => `/reservations/${id}/status`,
  reservationPayments: (id: number) => `/reservations/${id}/payments`,
  availability: '/reservations/availability',
  equipmentCollection: '/equipment',
  imageUploads: '/uploads/images',
  assistantChat: '/assistant/chat',
  equipmentRating: '/reviews/ratings',
} as const
