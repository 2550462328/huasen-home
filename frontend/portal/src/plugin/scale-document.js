/*
 * @Autor: huasenjio
 * @Date: 2022-09-03 13:59:36
 * @LastEditors: huasenjio
 * @LastEditTime: 2023-04-22 18:31:37
 * @Description: 触屏设备缩放可视窗口适应小屏；桌面大屏（4K 等）按基准宽度等比放大
 */

import { debounce } from 'lodash';
import CONSTENT from '@/constant/index.js';

// 不缩放开关
let noScale = false;

// 桌面端等比放大的基准宽度：屏幕宽度 >= 该值时按比例放大（1920→1x，3840→2x）
const DESKTOP_BASE_WIDTH = 1920;

// 在根节点应用 zoom，等价于浏览器原生缩放：viewport 单位自动重算，不会溢出留白
function applyDesktopZoom() {
  let el = document.documentElement;
  // 先复位再测量，拿到 zoom=1 下的真实 CSS 像素宽度，避免缩放后 clientWidth 变小导致反复横跳
  el.style.zoom = '';
  let scale = Math.max(1, el.clientWidth / DESKTOP_BASE_WIDTH);
  el.style.zoom = scale === 1 ? '' : scale;
}

// 桌面端 resize 回调
let scaleDesktop = debounce(applyDesktopZoom, 200);

// resize事件回调
let scaleDocument = debounce(e => {
  let viewport = document.getElementById('viewport');
  // 计算缩放比例
  let scale = document.body.clientWidth / CONSTENT.appMinWidth;
  if (scale === 1) {
    // 若比例等于1，就不再缩放，否则屏幕由于缩放，一直抖动
    noScale = true;
  } else if (noScale) {
    // 无需缩放
  } else if (scale < 1) {
    // 缩放
    viewport.content = `width=device-width,initial-scale=${scale}`;
    noScale = true;
  } else {
    // 复原
    viewport.content = 'width=device-width,initial-scale=1.0';
    noScale = false;
  }
}, 500);

// 初始化
function initScaleDocument() {
  if ('ontouchstart' in document.documentElement) {
    // 触屏设备：缩放可视窗口，适应小屏
    window.addEventListener('resize', scaleDocument);
    let event = new Event('resize', { bubbles: true, cancelable: false });
    document.dispatchEvent(event);
  } else {
    // 桌面设备：大屏等比放大（4K 等）
    applyDesktopZoom();
    window.addEventListener('resize', scaleDesktop);
  }
}

// 销毁
function destoryScaleDocument() {
  window.removeEventListener('resize', scaleDocument);
  window.removeEventListener('resize', scaleDesktop);
}

export { initScaleDocument, destoryScaleDocument };
