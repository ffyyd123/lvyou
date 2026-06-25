import AMapLoader from '@amap/amap-jsapi-loader'

let amapPromise = null

const plugins = [
  'AMap.Scale',
  'AMap.ToolBar',
  'AMap.ControlBar',
  'AMap.MoveAnimation',
  'AMap.DistrictSearch',
  'AMap.Driving'
]

export function loadAmap() {
  const apiKey = import.meta.env.VITE_AMAP_KEY
  if (!apiKey || apiKey === 'YOUR_AMAP_KEY') {
    return Promise.reject(new Error('请先在 .env 文件中配置高德地图 API Key（VITE_AMAP_KEY）'))
  }

  if (!amapPromise) {
    amapPromise = AMapLoader.load({
      key: apiKey,
      version: '2.0',
      plugins
    })
  }

  return amapPromise
}
