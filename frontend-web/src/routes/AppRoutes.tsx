import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import { FullPageSpinner } from '../components/common/Spinner'
import { AppLayout } from '../components/layout/AppLayout'
import { PATHS } from './paths'
import { GuestRoute, PrivateRoute } from './PrivateRoute'
import { PUBLISHER_ROLES } from './roles'

const LoginPage = lazy(() => import('../pages/LoginPage'))
const RegisterPage = lazy(() => import('../pages/RegisterPage'))
const DashboardPage = lazy(() => import('../pages/DashboardPage'))
const CatalogPage = lazy(() => import('../pages/CatalogPage'))
const EquipmentDetailPage = lazy(() => import('../pages/EquipmentDetailPage'))
const MyReservationsPage = lazy(() => import('../pages/MyReservationsPage'))
const PublishEquipmentPage = lazy(() => import('../pages/PublishEquipmentPage'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'))

export function AppRoutes() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <Routes>
        <Route element={<GuestRoute />}>
          <Route path={PATHS.login} element={<LoginPage />} />
          <Route path={PATHS.register} element={<RegisterPage />} />
        </Route>

        <Route element={<PrivateRoute />}>
          <Route element={<AppLayout />}>
            <Route path={PATHS.home} element={<DashboardPage />} />
            <Route path={PATHS.catalog} element={<CatalogPage />} />
            <Route path={PATHS.equipmentDetail} element={<EquipmentDetailPage />} />
            <Route path={PATHS.reservations} element={<MyReservationsPage />} />
            <Route element={<PrivateRoute allowedRoles={PUBLISHER_ROLES} />}>
              <Route path={PATHS.publishEquipment} element={<PublishEquipmentPage />} />
            </Route>
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
