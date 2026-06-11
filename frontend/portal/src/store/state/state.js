/*
 * @Autor: huasenjio
 * @Date: 2021-08-25 01:53:35
 * @LastEditors: huizhang43 huizhang43@iflytek.com
 * @LastEditTime: 2023-12-27 17:11:12
 * @Description: 数据状态树
 */
export default {
  // 用户数据
  user: {
    id: 'localhost@qq.com',
    name: '本地用户',
    headImg: '',
    token: '',
    code: 1,
    records: [],
    config: {
      // 色值｜图片链接| base64
      bg: require('@/assets/img/wallpaper/3.jpeg'),
      headerFontColor: '#ffffff',
      // 背景模糊度 0-10
      bgFilter: 0,
      // 背景明暗0-1（黑）
      bgLightness: 0,
      // 背景透明度 0-1
      bgOpacity: 0,
      // 背景文字灰度 0 -1000
      bgColor: 0,
      // 标题名称文字颜色
      titleColor: '#ffffff',
      // 明亮模式
      lightMode: false,
      // 极简模式
      simpleMode: false,
      // 配色
      theme: {},
    },
  },
  // 全部站点
  sites: [],
  // 选中订阅源的站点
  categorySites: [],
  // 侧栏折叠
  showWrapLeft: true,
  showWrapSidebar: true,
  showWrapSidebarSocket: false,
  showWrapConnect: false,
  showWrapSign: false,
  showWrapPerson: false,
  showForm: false,

  // 主题配置
  themeConfig: {
    pure: [],
    wallpaper: [],
    defaultWallpaper: {},
  },

  // 系统配置
  appConfig: {
    // 配别配置加载情况
    loaded: false,

    article: {
      id: '',
      course: '',
      about: '',
      help: '',
    },
    site: {
      name: '花森',
      logoURL: require('@/assets/img/logo/favicon.svg'),
      redirectURL: 'https://www.huitogo.club/',
      guidePageName: '花森小窝',
      guidePageUrl: 'https://www.huitogo.club/',
      footerHtml: '',
      serviceQRCodeUrl: require('@/assets/img/logo/weixin.png'),
      openLabelClassification: false,
    },
  },
};
