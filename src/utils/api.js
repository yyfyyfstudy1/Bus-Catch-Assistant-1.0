import axios from 'axios'

export const api = axios.create({
  // 本地 .env 里为空字符串 ⇒ 继续走 /api 代理
  // 线上 .env.production 指向官方域名
  baseURL: import.meta.env.VITE_API_BASE ?? '',
  headers:{
    Accept:'application/json'
  }
})
