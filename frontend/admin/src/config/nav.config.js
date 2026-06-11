/*
 * @Autor: huasenjio
 * @Date: 2022-10-07 10:06:24
 * @LastEditors: huasenjio
 * @LastEditTime: 2023-03-18 01:25:07
 * @Description: 跳转导航配置
 */

export default [
  // 核心系统
  { group: '核心系统' },
  {
    name: '数据表盘',
    path: '/home',
    iconClass: 'iconfont icon-md-planet',
  },
  {
    name: '订阅管理',
    path: '/journal-manage',
    iconClass: 'iconfont icon-a-smartrobot-fill',
  },
  {
    name: '栏目管理',
    path: '/column-manage',
    iconClass: 'iconfont icon-fenlei',
  },
  {
    name: '网链管理',
    path: '/site-manage',
    iconClass: 'iconfont  icon-md-link',
  },
  // 博客管理
  { group: '博客管理' },
  {
    name: '在档',
    path: '/tiny-blog-manage',
    iconClass: 'iconfont icon-md-stats',
  },
  {
    name: '已归档',
    path: '/blog-sharon-manage',
    iconClass: 'iconfont icon-md-stats',
  },
  // 系统
  { group: '系统' },
  {
    name: '账号管理',
    path: '/account-manage',
    iconClass: 'iconfont icon-md-happy',
  },
  {
    name: '系统配置',
    path: '/setting',
    iconClass: 'iconfont icon-xitongpeizhi',
  },
];
