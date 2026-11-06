/**
 * 基础数据 API：项目与系统两级主数据。
 *
 * 与 `/basic/projects`、`/basic/systems` 不是一套：
 * 那两个是建单表单的联动下拉，只回 id / code / name；
 * 这里是管理页用的完整字段与增删改。
 */
import { requestClient } from '#/api/request';

/** 分页返回体，与后端 PageResult 字段一致 */
export interface BasicPage<T> {
  items: T[];
  total: number;
}

/** 项目列表行 */
export interface ProjectRow {
  createdAt?: null | string;
  deptId?: null | string;
  deptName?: null | string;
  endDate?: null | string;
  id: string;
  ownerId?: null | string;
  ownerName?: null | string;
  projectCode: string;
  projectName: string;
  projectType?: null | string;
  remark?: null | string;
  sortNo?: null | number;
  startDate?: null | string;
  status: string;
  /** 本项目下的系统数量，删除前用来看挂了多少东西 */
  systemCount: number;
  /** 本项目下的需求单数量 */
  ticketCount: number;
}

/** 项目新增 / 修改提交体 */
export interface ProjectSaveParams {
  deptId?: string;
  endDate?: string;
  ownerId?: string;
  /** 仅新增时提交：编码创建后不可改 */
  projectCode?: string;
  projectName: string;
  projectType?: string;
  remark?: string;
  sortNo?: number;
  startDate?: string;
  status?: string;
}

/** 项目查询条件 */
export interface ProjectQueryParams {
  keyword?: string;
  page?: number;
  pageSize?: number;
  status?: string;
}

/** 系统列表行 */
export interface SystemRow {
  createdAt?: null | string;
  id: string;
  ownerId?: null | string;
  ownerName?: null | string;
  projectCode?: null | string;
  projectId: string;
  projectName?: null | string;
  remark?: null | string;
  sortNo?: null | number;
  status: string;
  systemCode: string;
  systemName: string;
  systemType?: null | string;
  /** 本系统下的需求单数量 */
  ticketCount: number;
}

/** 系统新增 / 修改提交体 */
export interface SystemSaveParams {
  ownerId?: string;
  /** 仅新增时提交：系统只属一个项目，创建后不可改 */
  projectId?: string;
  remark?: string;
  sortNo?: number;
  /** 仅新增时提交：编码创建后不可改 */
  systemCode?: string;
  systemName: string;
  systemType?: string;
  status?: string;
}

/** 系统查询条件 */
export interface SystemQueryParams {
  keyword?: string;
  page?: number;
  pageSize?: number;
  projectId?: string;
  status?: string;
}

/**
 * 分页查询项目。
 *
 * @param params 查询条件
 */
export async function listProjectApi(params: ProjectQueryParams) {
  return requestClient.get<BasicPage<ProjectRow>>('/basic/project/list', { params });
}

/**
 * 新增项目。
 *
 * @param data 项目信息
 */
export async function createProjectApi(data: ProjectSaveParams) {
  return requestClient.post<void>('/basic/project', data);
}

/**
 * 修改项目。编码不可改，提交体里带了不同编码后端会拒绝。
 *
 * @param id   项目 ID
 * @param data 项目信息
 */
export async function updateProjectApi(id: string, data: ProjectSaveParams) {
  return requestClient.put<void>(`/basic/project/${id}`, data);
}

/**
 * 删除项目。下挂系统或单据时后端拒绝并给出原因。
 *
 * @param id 项目 ID
 */
export async function deleteProjectApi(id: string) {
  return requestClient.delete<void>(`/basic/project/${id}`);
}

/**
 * 分页查询系统。
 *
 * @param params 查询条件
 */
export async function listSystemApi(params: SystemQueryParams) {
  return requestClient.get<BasicPage<SystemRow>>('/basic/system/list', { params });
}

/**
 * 新增系统。
 *
 * @param data 系统信息
 */
export async function createSystemApi(data: SystemSaveParams) {
  return requestClient.post<void>('/basic/system', data);
}

/**
 * 修改系统。所属项目与编码不可改。
 *
 * @param id   系统 ID
 * @param data 系统信息
 */
export async function updateSystemApi(id: string, data: SystemSaveParams) {
  return requestClient.put<void>(`/basic/system/${id}`, data);
}

/**
 * 删除系统。下挂单据时后端拒绝并给出原因。
 *
 * @param id 系统 ID
 */
export async function deleteSystemApi(id: string) {
  return requestClient.delete<void>(`/basic/system/${id}`);
}
