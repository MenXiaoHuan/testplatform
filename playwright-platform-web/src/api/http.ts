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

/**
 * Normalizes backend responses so API callers receive domain data directly.
 *
 * The backend normally returns ApiResponse envelopes, while 204 responses and
 * some static resources may not include a body.
 */
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

/** Sends a GET request and unwraps the platform response envelope. */
export function get<T>(url: string, config?: AxiosRequestConfig) {
  return http.get<ApiResponseEnvelope<T> | T>(url, config).then((response) => unwrapResponseData<T>(response))
}

/** Sends a POST request and unwraps the platform response envelope. */
export function post<T>(url: string, payload?: unknown, config?: AxiosRequestConfig) {
  return http
    .post<ApiResponseEnvelope<T> | T>(url, payload, config)
    .then((response) => unwrapResponseData<T>(response))
}

/** Sends a PUT request and unwraps the platform response envelope. */
export function put<T>(url: string, payload?: unknown, config?: AxiosRequestConfig) {
  return http
    .put<ApiResponseEnvelope<T> | T>(url, payload, config)
    .then((response) => unwrapResponseData<T>(response))
}

/** Sends a DELETE request and treats 204/no-body responses as void. */
export function del(url: string, config?: AxiosRequestConfig) {
  return http.delete(url, config).then(() => undefined)
}

export default http
