// 全局上下文：由 main.js 填充，供各视图与表单读取。
export const ctx = {
  store: null,
  api: null,
  user: null,
  /** 跳转到路由，如 go('timetable')。 */
  go: () => {},
  /** 重新渲染当前视图。 */
  rerender: () => {},
};
