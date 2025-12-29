import request from '@/utils/request'
import type {  ApiResponse, Department, UserDepartmentInfo, CacheStatus, UserInfoWithDepartment  } from '@/api'

// 获取部门列表
export const getDepartmentList = async (): Promise<ApiResponse<Department[]>> => {
  return request.get('/department/list')
}

// 根据部门获取区域
export const getRegionByDepartment = async (departmentId: string): Promise<ApiResponse<string>> => {
  return request.get('/department/getRegion', {
    params: { departmentId }
  })
}

// 获取用户部门信息
export const getUserDepartmentInfo = async (userId: string): Promise<ApiResponse<UserDepartmentInfo>> => {
  return request.get(`/user-department/info/${userId}`)
}

// 刷新用户-部门关系缓存
export const refreshUserDepartmentCache = async (): Promise<ApiResponse<{ message: string }>> => {
  return request.post('/user-department/refresh-cache')
}

// 获取缓存状态
export const getCacheStatus = async (): Promise<ApiResponse<CacheStatus>> => {
  return request.get('/user-department/cache-status')
}

// 根据部门ID获取部门成员
export const getDepartmentMembers = async (departmentId: string): Promise<ApiResponse<UserInfoWithDepartment[]>> => {
  return request.get(`/user-info/department/${departmentId}`)
}

// 获取用户的完整信息
export const getUserFullInfo = async (userId: string): Promise<ApiResponse<UserInfoWithDepartment>> => {
  return request.get(`/user-info/full/${userId}`)
}

// 构建部门树形结构
export const buildDepartmentTree = (departments: Department[]): Department[] => {
  const departmentMap = new Map<string, Department & { children?: Department[] }>()
  const rootDepartments: (Department & { children?: Department[] })[] = []
  
  // 创建映射
  departments.forEach(dept => {
    departmentMap.set(dept.id.toString(), { ...dept, children: [] })
  })
  
  // 构建树形结构
  departments.forEach(dept => {
    const department = departmentMap.get(dept.id.toString())
    if (dept.parentId && departmentMap.has(dept.parentId.toString())) {
      const parent = departmentMap.get(dept.parentId.toString())
      if (parent && department) {
        if (!parent.children) parent.children = []
        parent.children.push(department)
      }
    } else {
      if (department) {
        rootDepartments.push(department)
      }
    }
  })
  
  return rootDepartments
}

// 根据区域过滤部门
export const filterDepartmentsByRegion = (departments: Department[], region: string): Department[] => {
  if (!region) return departments
  
  // 这里需要根据实际业务逻辑实现区域过滤
  // 暂时返回所有部门，实际应该根据区域与部门的对应关系过滤
  return departments
}

// 获取部门层级路径
export const getDepartmentPath = (departments: Department[], departmentId: string): string[] => {
  const path: string[] = []
  let currentId = departmentId
  
  while (currentId) {
    const dept = departments.find(d => d.id.toString() === currentId)
    if (dept) {
      path.unshift(dept.name)
      currentId = dept.parentId?.toString() || ''
    } else {
      break
    }
  }
  
  return path
}