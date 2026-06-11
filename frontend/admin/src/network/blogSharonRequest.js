/*
 * @Description: blog-sharon 后台请求实例
 * 复用项目统一的鉴权头注入（token + dot），避免裸 axios 绕过拦截器导致 403。
 * 与 network/intercept.js 的请求拦截保持一致：headers.token / headers.dot。
 */
import axios from 'axios';
import state from '@/store/state/state.js';

const isDev = process.env.NODE_ENV === 'development';

const instance = axios.create({
  baseURL: isDev ? '/dev' : '/api',
  timeout: 180000,
  withCredentials: true,
});

// 每次请求注入管理端鉴权头（token 在登录后写入 state.manage.token）
instance.interceptors.request.use(
  config => {
    config.headers.dot = 'manage';
    config.headers.token = state.manage.token;
    return config;
  },
  error => Promise.reject(error),
);

export default instance;
