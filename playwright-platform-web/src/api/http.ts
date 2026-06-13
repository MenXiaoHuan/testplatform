import axios from 'axios'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'

export interface ApiResponseEnvelope<T> {
  code: string
  data: T
  msg: string
}

function isApiResponseEnvelope<T>(value: unknown): value is ApiResponseEnvelope<T> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    'data' in value &&
    'msg' in value
  )
}

export function unwrapResponseData<T>(response: AxiosResponse<ApiResponseEnvelope<T> | T>): T {
  if (response.status === 204) {
    return undefined as T
  }

  return isApiResponseEnvelope<T>(response.data) ? response.data.data : response.data
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

export function get<T>(url: string, config?: AxiosRequestConfig) {
  return http.get<ApiResponseEnvelope<T> | T>(url, config).then((response) => unwrapResponseData<T>(response))
}

export function post<T>(url: string, payload?: unknown, config?: AxiosRequestConfig) {
  return http
    .post<ApiResponseEnvelope<T> | T>(url, payload, config)
    .then((response) => unwrapResponseData<T>(response))
}

export function put<T>(url: string, payload?: unknown, config?: AxiosRequestConfig) {
  return http
    .put<ApiResponseEnvelope<T> | T>(url, payload, config)
    .then((response) => unwrapResponseData<T>(response))
}

export function del(url: string, config?: AxiosRequestConfig) {
  return http.delete(url, config).then(() => undefined)
}

export default http
