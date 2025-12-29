import { createPinia } from 'pinia'

const pinia = createPinia()

export { useAuthStore } from './auth'
export { useReimbursementStore } from './reimbursement'

export default pinia